package io.github.ih0rd.examples.controller;

import io.github.ih0rd.examples.contracts.ForecastService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/forecast")
public class ForecastController {

    private final ForecastService service;

    public ForecastController(ForecastService service) {
        this.service = service;
    }

    @PostMapping
    public Map<String, Object> forecast(
            @RequestBody List<Double> y,
            @RequestParam int steps,
            @RequestParam int seasonPeriod
    ) {
        return service.forecast(y, steps, seasonPeriod);
    }
}
