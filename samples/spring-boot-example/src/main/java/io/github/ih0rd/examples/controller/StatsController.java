package io.github.ih0rd.examples.controller;

import io.github.ih0rd.examples.contracts.StatsApi;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/stats")
public class StatsController {

    private final StatsApi stats;

    public StatsController(StatsApi stats) {
        this.stats = stats;
    }

    @GetMapping("/random")
    public List<Integer> random(@RequestParam int n) {
        return stats.randomNumbers(n);
    }

    @GetMapping("/data")
    public Map<String, Object> stats(@RequestParam int n) {
        return stats.stats(n);
    }

    @GetMapping("/table")
    public String table(@RequestParam int n) {
        return stats.formatStats(n);
    }
}
