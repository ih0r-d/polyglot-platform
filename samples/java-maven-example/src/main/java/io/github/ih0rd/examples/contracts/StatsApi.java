package io.github.ih0rd.examples.contracts;

import java.util.List;
import java.util.Map;

public interface StatsApi {

    // Returns a list of random integers
    List<Integer> randomNumbers(int n);

    // Returns calculated statistics
    Map<String, Object> stats(int n);

    // Returns formatted statistics table
    String formatStats(int n);
}