package io.github.ih0rd.examples;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.exporter.SpanExportingPredicate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Dev-only export filtering; observations, metrics and application tracing remain enabled. */
@Configuration(proxyBeanMethods = false)
@Profile("dev")
@ConditionalOnClass(SpanExportingPredicate.class)
public class DevTracingConfiguration {
  @Bean
  SpanExportingPredicate diagnosticPollingSpans(
      @Value("${polyglot.console.path:/polyglot-console}") String consolePath,
      @Value("${management.endpoints.web.base-path:/actuator}") String actuatorPath,
      @Value("${server.servlet.context-path:}") String contextPath) {
    String actuator =
        actuatorPath.endsWith("/")
            ? actuatorPath.substring(0, actuatorPath.length() - 1)
            : actuatorPath;
    List<String> roots =
        List.of(consolePath, actuator + "/metrics", actuator + "/health", actuator + "/info");
    return span -> {
      if (span.getKind() != Span.Kind.SERVER || !"GET".equals(span.getTags().get("method")))
        return true;
      String route = span.getTags().get("uri");
      if (route == null) return true;
      if (!contextPath.isEmpty() && route.startsWith(contextPath + "/"))
        route = route.substring(contextPath.length());
      for (String root : roots) {
        if (route.equals(root) || route.startsWith(root + "/")) return false;
      }
      return true;
    };
  }
}
