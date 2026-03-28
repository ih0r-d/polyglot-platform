package io.github.ih0rd.demo;

import io.github.ih0rd.demo.polyglot.KeywordExtractor;
import io.github.ih0rd.demo.polyglot.LanguageDetector;
import io.github.ih0rd.demo.polyglot.SummaryService;
import io.github.ih0rd.demo.polyglot.SentimentAnalyzer;
import io.github.ih0rd.demo.polyglot.TextCleaner;

import java.util.List;

public final class ReviewPipelineService {

    private static final System.Logger LOGGER =
            System.getLogger(ReviewPipelineService.class.getName());

    private final TextCleaner cleaner;
    private final LanguageDetector languageDetector;
    private final SentimentAnalyzer sentimentAnalyzer;
    private final KeywordExtractor keywordExtractor;
    private final SummaryService summaryService;

    public ReviewPipelineService(
            TextCleaner cleaner,
            LanguageDetector languageDetector,
            SentimentAnalyzer sentimentAnalyzer,
            KeywordExtractor keywordExtractor,
            SummaryService summaryService
    ) {
        this.cleaner = cleaner;
        this.languageDetector = languageDetector;
        this.sentimentAnalyzer = sentimentAnalyzer;
        this.keywordExtractor = keywordExtractor;
        this.summaryService = summaryService;
    }

    public DocumentAnalysisResult process(String document) {
        LOGGER.log(System.Logger.Level.INFO, "Starting document analysis pipeline...");

        String cleaned = cleaner.clean(document);
        LOGGER.log(System.Logger.Level.INFO, "Cleaned text: {0}", cleaned);

        String language = languageDetector.detect(cleaned);
        LOGGER.log(System.Logger.Level.INFO, "Language: {0}", language);

        double sentiment = sentimentAnalyzer.analyze(cleaned);
        LOGGER.log(System.Logger.Level.INFO, "Sentiment score: {0}", sentiment);

        List<String> keywords = keywordExtractor.extract(cleaned);
        LOGGER.log(System.Logger.Level.INFO, "Keywords: {0}", keywords);

        String summary = summaryService.summarize(cleaned);
        LOGGER.log(System.Logger.Level.INFO, "Summary: {0}", summary);
        LOGGER.log(System.Logger.Level.INFO, "Pipeline finished.");

        return new DocumentAnalysisResult(
                cleaned,
                language,
                sentiment,
                keywords,
                summary
        );
    }
}
