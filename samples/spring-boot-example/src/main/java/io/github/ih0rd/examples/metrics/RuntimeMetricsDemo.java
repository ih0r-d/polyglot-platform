package io.github.ih0rd.examples.metrics;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.exceptions.BindingException;
import io.github.ih0rd.adapter.exceptions.InvocationException;
import io.github.ih0rd.examples.contracts.RiskApi;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.config.ScriptSource;

/**
 * Exercises existing clients and reload APIs once; no reload endpoint or file watcher is exposed.
 */
final class RuntimeMetricsDemo implements ApplicationRunner {
  private static final Logger LOG = LoggerFactory.getLogger(RuntimeMetricsDemo.class);
  private static final String MODULE = "risk_api";
  private static final String KEY = SupportedLanguage.PYTHON.id() + "/" + MODULE;
  private static final List<Double> SAMPLES = List.of(10.0, 10.0, 10.0, 10.0, 100.0);
  private final PyExecutor executor;
  private final RiskApi client;
  private final ScriptSource scripts;
  private final Map<String, String> overrides;

  RuntimeMetricsDemo(
      PyExecutor executor, RiskApi client, ScriptSource scripts, Map<String, String> overrides) {
    this.executor = executor;
    this.client = client;
    this.scripts = scripts;
    this.overrides = overrides;
  }

  @Override
  public void run(ApplicationArguments arguments) throws IOException {
    String original = originalSource();
    client.assess(SAMPLES, 1.5);
    overrides.put(KEY, original);
    executor.reloadContract(RiskApi.class, Convention.BY_METHOD_NAME);
    client.assess(SAMPLES, 1.5);
    rejectReplacement(original);
    try {
      client.fail();
    } catch (InvocationException expected) {
      LOG.debug("Expected guest failure in runtime metrics demo", expected);
    }
    LOG.info(
        "Runtime metrics demo completed with the same RiskApi client. Inspect reload and cache metrics; use demo.sh load for real HTTP contention.");
  }

  private void rejectReplacement(String original) throws IOException {
    try (InputStream source =
        getClass().getResourceAsStream("/runtime-metrics-demo/incompatible_risk_api.py")) {
      if (source == null) throw new IOException("Missing runtime metrics demo replacement");
      overrides.put(KEY, new String(source.readAllBytes(), StandardCharsets.UTF_8));
    }
    try {
      executor.reloadContract(RiskApi.class, Convention.BY_METHOD_NAME);
    } catch (BindingException expected) {
      LOG.debug("Expected replacement rejection in runtime metrics demo", expected);
      client.assess(SAMPLES, 1.5);
    } finally {
      overrides.put(KEY, original);
      executor.reloadContract(RiskApi.class, Convention.BY_METHOD_NAME);
    }
  }

  private String originalSource() throws IOException {
    try (Reader reader = scripts.open(SupportedLanguage.PYTHON, MODULE)) {
      StringWriter result = new StringWriter();
      reader.transferTo(result);
      return result.toString();
    }
  }
}
