package io.github.ih0rd.polyglot.console.internal.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.web.util.UriComponentsBuilder;

import io.github.ih0rd.polyglot.console.properties.ConsoleProperties;

/** Announces the local console only after its own embedded application is ready. */
public final class ConsoleStartupLogger implements ApplicationListener<ApplicationReadyEvent> {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleStartupLogger.class);

  private final ApplicationContext owner;
  private final ConsoleProperties console;
  private final ObjectProvider<ServerProperties> serverProperties;

  /**
   * Creates the console startup listener without starting or probing a server.
   *
   * @param owner application context containing this console
   * @param console validated console configuration
   * @param serverProperties optional embedded-server configuration
   */
  public ConsoleStartupLogger(
      ApplicationContext owner,
      ConsoleProperties console,
      ObjectProvider<ServerProperties> serverProperties) {
    this.owner = owner;
    this.console = console;
    this.serverProperties = serverProperties;
  }

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    if (event.getApplicationContext() != owner
        || !(owner instanceof ServletWebServerApplicationContext application)) {
      return;
    }
    var server = application.getWebServer();
    var servlet = application.getServletContext();
    if (server == null || servlet == null) {
      return;
    }
    var configured = serverProperties.getIfAvailable();
    var ssl = configured == null ? null : configured.getSsl();
    String url =
        UriComponentsBuilder.newInstance()
            .scheme(ssl != null && ssl.isEnabled() ? "https" : "http")
            .host("localhost")
            .port(server.getPort())
            .path(servlet.getContextPath())
            .path(console.path())
            .path("/")
            .build()
            .toUriString();
    LOGGER.info(
        "Polyglot Console available at {} (local access; application security applies)", url);
  }
}
