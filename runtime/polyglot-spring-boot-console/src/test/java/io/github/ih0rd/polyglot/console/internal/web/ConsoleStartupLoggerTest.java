package io.github.ih0rd.polyglot.console.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.mock.web.MockServletContext;

import io.github.ih0rd.polyglot.console.properties.ConsoleProperties;

@ExtendWith(OutputCaptureExtension.class)
class ConsoleStartupLoggerTest {
  @Test
  void logsActualPortAndCustomPathsWhenReady(CapturedOutput output) {
    var application = application(8545, "/consumer");
    logger(application, "/ops/console", null).onApplicationEvent(ready(application));
    assertThat(output.getOut())
        .contains("Polyglot Console available at http://localhost:8545/consumer/ops/console/");
  }

  @Test
  void logsConfiguredHttpsScheme(CapturedOutput output) {
    var properties = new ServerProperties();
    var ssl = new Ssl();
    ssl.setEnabled(true);
    properties.setSsl(ssl);
    var application = application(8443, "");
    logger(application, "/polyglot-console", properties).onApplicationEvent(ready(application));
    assertThat(output.getOut()).contains("https://localhost:8443/polyglot-console/");
  }

  @Test
  void ignoresEventsFromOtherApplicationContexts(CapturedOutput output) {
    var owner = application(8545, "");
    logger(owner, "/polyglot-console", null)
        .onApplicationEvent(ready(application(9001, "/management")));
    assertThat(output.getOut()).doesNotContain("Polyglot Console available");
  }

  @Test
  void doesNotInventPortWhenNoEmbeddedServerIsAvailable(CapturedOutput output) {
    var owner = mock(ServletWebServerApplicationContext.class);
    logger(owner, "/polyglot-console", null).onApplicationEvent(ready(owner));
    assertThat(output.getOut()).doesNotContain("Polyglot Console available");
  }

  private static ConsoleStartupLogger logger(
      ServletWebServerApplicationContext application,
      String path,
      @org.jspecify.annotations.Nullable ServerProperties configured) {
    var factory = new DefaultListableBeanFactory();
    if (configured != null) factory.registerSingleton("serverProperties", configured);
    return new ConsoleStartupLogger(
        application,
        new ConsoleProperties(true, path, Duration.ofSeconds(5)),
        factory.getBeanProvider(ServerProperties.class));
  }

  private static ServletWebServerApplicationContext application(int port, String path) {
    var application = mock(ServletWebServerApplicationContext.class);
    var server = mock(WebServer.class);
    var servlet = new MockServletContext();
    servlet.setContextPath(path);
    when(server.getPort()).thenReturn(port);
    when(application.getWebServer()).thenReturn(server);
    when(application.getServletContext()).thenReturn(servlet);
    return application;
  }

  private static ApplicationReadyEvent ready(ServletWebServerApplicationContext application) {
    return new ApplicationReadyEvent(
        new SpringApplication(Object.class), new String[0], application, Duration.ZERO);
  }
}
