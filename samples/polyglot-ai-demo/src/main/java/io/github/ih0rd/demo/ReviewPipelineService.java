package io.github.ih0rd.demo;

import io.github.ih0rd.demo.polyglot.TextCleaner;
import io.github.ih0rd.demo.polyglot.LanguageDetector;
import io.github.ih0rd.demo.polyglot.SentimentAnalyzer;
import io.github.ih0rd.demo.polyglot.KeywordExtractor;
import io.github.ih0rd.demo.polyglot.SummaryService;
import io.github.ih0rd.demo.DocumentAnalysisResult;

import java.util.List;

public class ReviewPipelineService {

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

        System.out.println("Starting document analysis pipeline...");
        System.out.println();

        String cleaned = cleaner.clean(document);
        System.out.println("Cleaned text: " + cleaned);

        String language = languageDetector.detect(cleaned);
        System.out.println("Language: " + language);

        double sentiment = sentimentAnalyzer.analyze(cleaned);
        System.out.println("Sentiment score: " + sentiment);

        List<String> keywords = keywordExtractor.extract(cleaned);
        System.out.println("Keywords: " + keywords);

        String summary = summaryService.summarize(cleaned);
        System.out.println("Summary: " + summary);

        System.out.println();
        System.out.println("Pipeline finished.");

        return new DocumentAnalysisResult(
                cleaned,
                language,
                sentiment,
                keywords,
                summary
        );
    }
}