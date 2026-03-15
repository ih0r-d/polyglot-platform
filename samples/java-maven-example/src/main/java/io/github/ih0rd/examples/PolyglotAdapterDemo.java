package io.github.ih0rd.examples;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PolyglotHelper;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.contract.SupportedLanguage;
import io.github.ih0rd.contract.ScriptSource;
import io.github.ih0rd.examples.contracts.BrokenForecastService;
import io.github.ih0rd.examples.contracts.ForecastService;
import io.github.ih0rd.examples.contracts.StatsApi;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class PolyglotAdapterDemo {

    private static final Path PY_SCRIPT_DIR = Path.of("src/main/python");
    private static final Path JS_SCRIPT_DIR = Path.of("src/main/js");

    private static final int SEPARATOR_REPEAT = 10;

    private static final List<Double> DATA =
            List.of(2.0, 3.5, 4.2, 5.0, 6.1, 8.3);

    private static final int STEPS = 3;
    private static final int PERIOD = 2;

    public static void main(String[] args) {
        new PolyglotAdapterDemo().run();
    }

    void run() {
        IO.println("=== Polyglot Adapter Demo (GraalVM 25) ===");

        ScriptSource pyScriptSource = new FileSystemScriptSource(PY_SCRIPT_DIR);
        ScriptSource jsScriptSource = new FileSystemScriptSource(JS_SCRIPT_DIR);

        IO.println("[STEP 1] Python – default context");
        step1RunPython(pyScriptSource);
        printSeparator();

        IO.println("[STEP 2] Python – custom context");
        step2RunPythonWithContext(pyScriptSource);
        printSeparator();

        IO.println("[STEP 3] JavaScript – default context");
        step3RunJs(jsScriptSource);
        printSeparator();

        IO.println("[STEP 4] Python – binding validation (OK + broken)");
        step4RunPythonWithBrokenValidation(pyScriptSource);
        printSeparator();

        IO.println("[STEP 5] JavaScript – binding validation (OK + broken)");
        step5RunJsWithBrokenValidation(jsScriptSource);
        printSeparator();

        IO.println("[STEP 6] Executors metadata");
        step6PrintMetadata(jsScriptSource,pyScriptSource);

        IO.println("=== Demo Completed ===");
    }

    private void printSeparator() {
        IO.println("=== === ".repeat(SEPARATOR_REPEAT));
    }

    private void step1RunPython(ScriptSource scriptSource) {
        try (var ctx = PolyglotHelper.newContext(SupportedLanguage.PYTHON);
                var executor = new PyExecutor(ctx, scriptSource)) {

            StatsApi statsApi = executor.bind(StatsApi.class);

            List<Integer> randomNumbers =
                    statsApi.randomNumbers(SEPARATOR_REPEAT);
            IO.println("random numbers -> " + randomNumbers);

            Map<String, Object> statsMap =
                    statsApi.stats(SEPARATOR_REPEAT);
            IO.println("stats -> " + statsMap);

            String formatted =
                    statsApi.formatStats(SEPARATOR_REPEAT);
            IO.println("formatStats -> " + formatted);
        }
    }

    private void step2RunPythonWithContext(ScriptSource scriptSource) {
        try (var ctx = PolyglotHelper.newContext(
                SupportedLanguage.PYTHON,
                b -> b.option("engine.WarnInterpreterOnly", "false")
                        .option("python.WarnExperimentalFeatures", "false"));
                var executor = new PyExecutor(ctx, scriptSource)) {

            ForecastService service =
                    executor.bind(ForecastService.class);

            Map<String, Object> forecast =
                    service.forecast(DATA, STEPS, PERIOD);

            IO.println("py forecast -> " + forecast);
        }
    }

    private void step3RunJs(ScriptSource scriptSource) {
        try (var ctx = PolyglotHelper.newContext(SupportedLanguage.JS);
                var executor = new JsExecutor(ctx, scriptSource)) {

            executor.validateBinding(ForecastService.class);

            ForecastService service =
                    executor.bind(ForecastService.class);

            Map<String, Object> forecast =
                    service.forecast(DATA, STEPS, PERIOD);

            IO.println("js forecast -> " + forecast);
        }
    }

    private void step4RunPythonWithBrokenValidation(ScriptSource scriptSource) {
        try (var ctx = PolyglotHelper.newContext(SupportedLanguage.PYTHON);
                var py = new PyExecutor(ctx, scriptSource)) {

            try {
                py.validateBinding(ForecastService.class);
                IO.println("[PY] ForecastService binding OK");
            } catch (Exception e) {
                IO.println("[PY] ForecastService FAILED (unexpected): " + e.getMessage());
            }

            try {
                py.validateBinding(BrokenForecastService.class);
                IO.println("[PY] BrokenForecastService UNEXPECTED OK");
            } catch (Exception e) {
                IO.println("[PY] BrokenForecastService FAILED (expected): " + e.getMessage());
            }
        }
    }

    private void step5RunJsWithBrokenValidation(ScriptSource scriptSource) {
        try (var ctx = PolyglotHelper.newContext(SupportedLanguage.JS);
                var js = new JsExecutor(ctx, scriptSource)) {

            try {
                js.validateBinding(ForecastService.class);
                IO.println("[JS] ForecastService binding OK");
            } catch (Exception e) {
                IO.println("[JS] ForecastService FAILED (unexpected): " + e.getMessage());
            }

            try {
                js.validateBinding(BrokenForecastService.class);
                IO.println("[JS] BrokenForecastService UNEXPECTED OK");
            } catch (Exception e) {
                IO.println("[JS] BrokenForecastService FAILED (expected): " + e.getMessage());
            }
        }
    }

    private void step6PrintMetadata(ScriptSource jsScriptSource,ScriptSource pyScriptSource) {
        IO.println("[STEP 6] Executor metadata snapshot");

        try (var pyCtx = PolyglotHelper.newContext(SupportedLanguage.PYTHON);
                var jsCtx = PolyglotHelper.newContext(SupportedLanguage.JS);
                var py = new PyExecutor(pyCtx, pyScriptSource);
                var js = new JsExecutor(jsCtx, jsScriptSource)) {

            py.validateBinding(StatsApi.class);
            js.validateBinding(ForecastService.class);

            IO.println("[PY] metadata -> " + py.metadata());
            IO.println("[JS] metadata -> " + js.metadata());
        }
    }
}
