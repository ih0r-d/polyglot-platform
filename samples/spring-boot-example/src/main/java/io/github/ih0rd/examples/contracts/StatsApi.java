package io.github.ih0rd.examples.contracts;

import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.PolyglotClient;
import java.util.List;
import java.util.Map;

@PolyglotClient(languages = SupportedLanguage.PYTHON)
public interface StatsApi {

    // Returns a list of random integers
    List<Integer> randomNumbers(int n);

    // Returns calculated statistics
    Map<String, Object> stats(int n);

    // Returns formatted statistics table
    String formatStats(int n);
}
