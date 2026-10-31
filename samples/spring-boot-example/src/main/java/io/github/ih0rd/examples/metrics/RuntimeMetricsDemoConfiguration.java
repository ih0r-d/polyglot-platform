package io.github.ih0rd.examples.metrics;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ResourceLoader;

import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.spi.CompositeScriptSource;
import io.github.ih0rd.adapter.spi.InMemoryScriptSource;
import io.github.ih0rd.examples.contracts.RiskApi;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;
import io.github.ih0rd.polyglot.annotations.spring.script.SpringResourceScriptSource;
import io.github.ih0rd.polyglot.model.config.ScriptSource;

/** Explicit development-only profile; ordinary analytics startup remains unchanged. */
@Configuration(proxyBeanMethods = false)
@Profile("runtime-metrics-demo")
public class RuntimeMetricsDemoConfiguration {
  private final Map<String, String> overrides = new ConcurrentHashMap<>();

  @Bean("pyScriptSource")
  ScriptSource demoScriptSource(ResourceLoader resources, PolyglotProperties properties) {
    return new CompositeScriptSource(
        List.of(
            new InMemoryScriptSource(overrides),
            new SpringResourceScriptSource(
                resources, SupportedLanguage.PYTHON, properties.python().resourcesPath())));
  }

  @Bean
  RuntimeMetricsDemo runtimeMetricsDemo(
      PyExecutor executor, RiskApi client, ScriptSource pyScriptSource) {
    return new RuntimeMetricsDemo(executor, client, pyScriptSource, overrides);
  }
}
