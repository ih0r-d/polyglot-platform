package io.github.ih0rd.demo;

import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.spi.ClasspathScriptSource;
import io.github.ih0rd.demo.polyglot.KeywordExtractor;
import io.github.ih0rd.demo.polyglot.LanguageDetector;
import io.github.ih0rd.demo.polyglot.SentimentAnalyzer;
import io.github.ih0rd.demo.polyglot.SummaryService;
import io.github.ih0rd.demo.polyglot.TextCleaner;

public final class DemoApplication {

    private DemoApplication() {}

    public static void main(String[] args) {
        var scriptSource = new ClasspathScriptSource();

        try (var executor = PyExecutor.create(scriptSource, null)) {
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
