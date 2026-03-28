package io.github.ih0rd.demo;

import java.util.List;

public record DocumentAnalysisResult(
        String cleanedText,
        String language,
        double sentiment,
        List<String> keywords,
        String summary
) {}
