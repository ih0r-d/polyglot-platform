package io.github.ih0rd.examples;

import io.github.ih0rd.adapter.context.PolyglotHelper;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.spi.ClasspathScriptSource;
import io.github.ih0rd.contract.ScriptSource;
import io.github.ih0rd.contract.SupportedLanguage;
import io.github.ih0rd.examples.polyglot.Libraries;
import io.github.ih0rd.examples.polyglot.LibrariesModule;

public final class PolyglotCodegenDemo {
    void main() {
        ScriptSource source = new ClasspathScriptSource();
        runClassBased(source);
        runModuleBased(source);
    }
    private static void runClassBased(ScriptSource source) {
        try (var ctx = PolyglotHelper.newContext(SupportedLanguage.PYTHON);
                var executor = new PyExecutor(ctx, source)) {
            Libraries api = executor.bind(Libraries.class);
            IO.println("Libraries classes table: ");
            IO.println(api.formatUsers(3));
        }
    }
    private static void runModuleBased(ScriptSource source) {
        try (var ctx = PolyglotHelper.newContext(SupportedLanguage.PYTHON);
                var executor = new PyExecutor(ctx, source)) {
            LibrariesModule module = executor.bind(LibrariesModule.class);

            IO.println("Libraries modules table: ");
            IO.println(module.formatUsers(3));
        }
    }
}