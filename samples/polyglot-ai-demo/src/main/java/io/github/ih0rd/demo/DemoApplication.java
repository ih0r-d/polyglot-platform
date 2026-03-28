package io.github.ih0rd.demo;

import io.github.ih0rd.adapter.context.PolyglotHelper;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.spi.ClasspathScriptSource;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.demo.polyglot.KeywordExtractor;
import io.github.ih0rd.demo.polyglot.LanguageDetector;
import io.github.ih0rd.demo.polyglot.SentimentAnalyzer;
import io.github.ih0rd.demo.polyglot.SummaryService;
import io.github.ih0rd.demo.polyglot.TextCleaner;

public final class DemoApplication {

    private DemoApplication() {}

    public static void main(String[] args) {
        var scriptSource = new ClasspathScriptSource();

        try (var ctx = PolyglotHelper.newContext(SupportedLanguage.PYTHON);
                var executor = new PyExecutor(ctx, scriptSource)) {
            TextCleaner cleaner = executor.bind(TextCleaner.class);
            LanguageDetector languageDetector = executor.bind(LanguageDetector.class);
            SentimentAnalyzer sentimentAnalyzer = executor.bind(SentimentAnalyzer.class);
            KeywordExtractor keywordExtractor = executor.bind(KeywordExtractor.class);
            SummaryService summaryService = executor.bind(SummaryService.class);

            ReviewPipelineService pipeline = new ReviewPipelineService(
                    cleaner,
                    languageDetector,
                    sentimentAnalyzer,
                    keywordExtractor,
                    summaryService
            );

            String review = """
                    The battery life of this phone is amazing.
                    However the camera quality is quite disappointing.
                    """;

            pipeline.process(review);
        }
    }
}
