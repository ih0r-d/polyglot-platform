package io.github.ih0rd.examples.aot;

import io.github.ih0rd.adapter.context.PolyglotHelper;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.spi.ClasspathScriptSource;
import io.github.ih0rd.polyglot.SupportedLanguage;

public final class Main {

    private static final System.Logger LOGGER = System.getLogger(Main.class.getName());

    private Main() {}

    public static void main(String[] args) {
        PricingRules rules = new PricingRules(0.20);
        ClasspathScriptSource scriptSource = new ClasspathScriptSource();

        try (var context = PolyglotHelper.newContext(SupportedLanguage.PYTHON);
             var pyExecutor = PyExecutor.createWithContext(context, scriptSource)) {
            context.getBindings("python").putMember("pricing_rules", rules);

            QuoteApi quoteApi = pyExecutor.bind(QuoteApi.class);
            var quote = quoteApi.calculateQuote(100.0, "GOLD");

            LOGGER.log(System.Logger.Level.INFO, "Quote: {0}", quote);
        }
    }
}
