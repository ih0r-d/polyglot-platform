package io.github.ih0rd.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.github.ih0rd.adapter.exceptions.InvocationException;
import io.github.ih0rd.examples.contracts.AnalyticsApi;
import io.github.ih0rd.examples.contracts.RiskApi;
import io.github.ih0rd.examples.controller.AnalyticsController;
import io.github.ih0rd.examples.controller.AnalyticsExceptionHandler;
import io.github.ih0rd.examples.dto.AnalyticsSummary;
import io.github.ih0rd.examples.dto.RiskAssessment;
import io.github.ih0rd.examples.dto.RollingResult;
import io.github.ih0rd.examples.validation.AnalyticsInputValidator;

/** Real MVC binding/advice, with numerical execution tested separately against GraalPy. */
class AnalyticsWebTest {
  private AnalyticsApi analytics;
  private RiskApi risk;
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    analytics = mock(AnalyticsApi.class);
    risk = mock(RiskApi.class);
    mvc =
        MockMvcBuilders.standaloneSetup(
                new AnalyticsController(analytics, risk, new AnalyticsInputValidator()))
            .setControllerAdvice(new AnalyticsExceptionHandler())
            .setMessageConverters(new GsonHttpMessageConverter())
            .build();
  }

  @Test
  void serializesTypedResultsAndAppliesDefaultThreshold() throws Exception {
    when(analytics.describe(anyList()))
        .thenReturn(new AnalyticsSummary(3, 2, 2, 1, 1, 3, 1.1, 2.9, "2.2.4"));
    when(analytics.rolling(anyList(), eq(2)))
        .thenReturn(new RollingResult(2, 1, List.of(1.5, 2.5)));
    when(risk.assess(anyList(), eq(1.5)))
        .thenReturn(new RiskAssessment(1.5, List.of(0.0), List.of(), 0, 0, 100));
    for (String path :
        List.of("/analytics/summary", "/analytics/rolling?window=2", "/analytics/risk")) {
      String body =
          mvc.perform(
                  post(path)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"samples\":[1,2,3]}"))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(body).startsWith("{").doesNotContain("NaN", "Infinity");
    }
  }

  @Test
  void rejectsMalformedBodyAndParametersBeforePython() throws Exception {
    for (String body : List.of("{", "{}", "{\"samples\":[]}", "{\"samples\":[null]}")) {
      String response =
          mvc.perform(
                  post("/analytics/summary").contentType(MediaType.APPLICATION_JSON).content(body))
              .andExpect(status().isBadRequest())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(response).contains("INVALID_INPUT");
    }
    for (String path :
        List.of(
            "/analytics/rolling",
            "/analytics/rolling?window=abc",
            "/analytics/rolling?window=0",
            "/analytics/rolling?window=4",
            "/analytics/risk?threshold=NaN",
            "/analytics/risk?threshold=0")) {
      String response =
          mvc.perform(
                  post(path)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"samples\":[1,2,3]}"))
              .andExpect(status().isBadRequest())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(response).contains("INVALID_INPUT");
    }
    verifyNoInteractions(analytics, risk);
  }

  @Test
  void hidesGuestDetailsFromHttpErrors() throws Exception {
    when(risk.assess(anyList(), eq(1.5)))
        .thenThrow(new InvocationException("sensitive guest source/password"));
    String response =
        mvc.perform(
                post("/analytics/risk")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"samples\":[1]}"))
            .andExpect(status().isInternalServerError())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(response)
        .contains("PYTHON_EXECUTION_FAILED")
        .doesNotContain("sensitive", "password");
  }
}
