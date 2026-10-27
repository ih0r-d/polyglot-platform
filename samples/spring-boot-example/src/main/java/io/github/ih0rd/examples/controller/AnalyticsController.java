package io.github.ih0rd.examples.controller;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.ih0rd.examples.contracts.AnalyticsApi;
import io.github.ih0rd.examples.contracts.RiskApi;
import io.github.ih0rd.examples.dto.AnalyticsSummary;
import io.github.ih0rd.examples.dto.RiskAssessment;
import io.github.ih0rd.examples.dto.RollingResult;
import io.github.ih0rd.examples.dto.SeriesRequest;
import io.github.ih0rd.examples.validation.AnalyticsInputValidator;

/** HTTP orchestration; numerical analytics runs in embedded Python. */
@RestController
@RequestMapping("/analytics")
public class AnalyticsController {
  private final AnalyticsApi analytics;
  private final RiskApi risk;
  private final AnalyticsInputValidator validation;

  public AnalyticsController(
      AnalyticsApi analytics, RiskApi risk, AnalyticsInputValidator validation) {
    this.analytics = analytics;
    this.risk = risk;
    this.validation = validation;
  }

  @PostMapping("/summary")
  public AnalyticsSummary summary(@RequestBody SeriesRequest request) {
    return analytics.describe(validation.samples(request));
  }

  @PostMapping("/rolling")
  public RollingResult rolling(@RequestBody SeriesRequest request, @RequestParam int window) {
    List<Double> values = validation.samples(request);
    validation.window(window, values.size());
    return analytics.rolling(values, window);
  }

  @PostMapping("/risk")
  public RiskAssessment risk(
      @RequestBody SeriesRequest request, @RequestParam(defaultValue = "1.5") double threshold) {
    List<Double> values = validation.samples(request);
    validation.threshold(threshold);
    return risk.assess(values, threshold);
  }

  @PostMapping("/failure")
  public void failure() {
    risk.fail();
  }
}
