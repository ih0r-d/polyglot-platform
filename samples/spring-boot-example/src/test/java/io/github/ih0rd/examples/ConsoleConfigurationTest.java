package io.github.ih0rd.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ih0rd.examples.console.ConsoleConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class ConsoleConfigurationTest {
  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withUserConfiguration(MvcConfiguration.class, ConsoleConfiguration.class);

  @Test
  void assetsAreNotExposedByDefaultOrWhenDisabled() {
    runner.run(
        context -> {
          assertThat(context).doesNotHaveBean(ConsoleConfiguration.class);
          MockMvcBuilders.webAppContextSetup(context)
              .build()
              .perform(get("/polyglot-console/index.html"))
              .andExpect(status().isNotFound());
        });
    enabled()
        .withPropertyValues("showcase.console.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(ConsoleConfiguration.class));
    runner
        .withPropertyValues("showcase.console.enabled=true", "server.address=127.0.0.1")
        .run(context -> assertThat(context).hasSingleBean(ConsoleConfiguration.class));
  }

  @Test
  void servesLocalAssetsWithRestrictiveHeaders() {
    enabled()
        .run(
            context -> {
              MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
              mvc.perform(get("/polyglot-console"))
                  .andExpect(status().is3xxRedirection())
                  .andExpect(header().string("Location", "/polyglot-console/"));
              for (String asset :
                  new String[] {
                    "index.html",
                    "console.css",
                    "bootstrap.js",
                    "app.js",
                    "api.js",
                    "model.js",
                    "polling.js",
                    "view.js"
                  }) {
                mvc.perform(get("/polyglot-console/" + asset))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(
                        header()
                            .string(
                                "Content-Security-Policy",
                                org.hamcrest.Matchers.containsString("script-src 'self'")));
              }
              mvc.perform(get("/polyglot-console/missing.js")).andExpect(status().isNotFound());
            });
  }

  @Test
  void rejectsRemoteClientsAndUnrecognizedHostNames() {
    enabled()
        .run(
            context -> {
              MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
              mvc.perform(
                      get("/polyglot-console/index.html")
                          .with(
                              request -> {
                                request.setRemoteAddr("192.0.2.1");
                                return request;
                              }))
                  .andExpect(status().isForbidden());
              mvc.perform(
                      get("/polyglot-console/index.html")
                          .with(
                              request -> {
                                request.setServerName("untrusted.example");
                                return request;
                              }))
                  .andExpect(status().isForbidden());
            });
  }

  @Test
  void refusesWildcardServerBinding() {
    enabled()
        .withPropertyValues("server.address=0.0.0.0")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(ApplicationContextException.class)
                    .hasStackTraceContaining("requires server.address"));
  }

  private WebApplicationContextRunner enabled() {
    return runner
        .withPropertyValues("showcase.console.enabled=true", "server.address=127.0.0.1");
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  static class MvcConfiguration {}
}
