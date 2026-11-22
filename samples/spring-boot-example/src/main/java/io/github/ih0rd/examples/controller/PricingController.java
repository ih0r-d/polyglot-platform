package io.github.ih0rd.examples.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.github.ih0rd.examples.contracts.DeliveryEstimateApi;
import io.github.ih0rd.examples.dto.PricingQuote;
import io.github.ih0rd.examples.generated.PricingApi;

/** Serves a volume-discount quote and delivery estimate calculated in Python. */
@RestController
public class PricingController {
  private final PricingApi pricing;
  private final DeliveryEstimateApi delivery;

  public PricingController(PricingApi pricing, DeliveryEstimateApi delivery) {
    this.pricing = pricing;
    this.delivery = delivery;
  }

  @GetMapping("/pricing/quote")
  public PricingQuote quote(
      @RequestParam("unitPriceCents") int unitPriceCents,
      @RequestParam("quantity") int quantity,
      @RequestParam("destination") String destination) {
    if (unitPriceCents < 1 || unitPriceCents > 1_000_000 || quantity < 1 || quantity > 1_000) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Use unitPriceCents in 1..1000000 and quantity in 1..1000");
    }
    if (!"local".equals(destination) && !"international".equals(destination)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Destination must be local or international");
    }

    return new PricingQuote(
        pricing.quote(unitPriceCents, quantity), delivery.estimate(destination));
  }
}
