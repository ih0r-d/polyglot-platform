package io.github.ih0rd.examples.aot;

import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.spi.ClasspathScriptSource;
import org.graalvm.polyglot.Context;

public class Main {
    public static void main(String[] args) {
        PricingRules rules = new PricingRules(0.20);
        ClasspathScriptSource scriptSource = new ClasspathScriptSource();

        try (var context = Context.newBuilder("python")
                     .allowAllAccess(true)
                     .allowExperimentalOptions(true)
                     .option("engine.WarnInterpreterOnly", "false")
                     .option("python.WarnExperimentalFeatures", "false")
                     .build();
             var pyExecutor = new PyExecutor(context, scriptSource)) {

            context.initialize("python");
            context.getBindings("python").putMember("pricing_rules", rules);

            QuoteApi quoteApi = pyExecutor.bind(QuoteApi.class);
            var quote = quoteApi.calculateQuote(100.0, "GOLD");

            System.out.println("Quote: " + quote);
        }
    }
}
