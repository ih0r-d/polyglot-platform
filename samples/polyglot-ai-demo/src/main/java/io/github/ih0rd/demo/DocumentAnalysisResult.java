package io.github.ih0rd.demo;

import java.util.List;

public class DocumentAnalysisResult {

    private final String cleanedText;
    private final String language;
    private final double sentiment;
    private final List<String> keywords;
    private final String summary;

    public DocumentAnalysisResult(
            String cleanedText,
            String language,
            double sentiment,
            List<String> keywords,
            String summary
    ) {
        this.cleanedText = cleanedText;
        this.language = language;
        this.sentiment = sentiment;
        this.keywords = keywords;
        this.summary = summary;
    }

    public String cleanedText() {
        return cleanedText;
    }

    public String language() {
        return language;
    }

    public double sentiment() {
        return sentiment;
    }

    public List<String> keywords() {
        return keywords;
    }

    public String summary() {
        return summary;
    }
}