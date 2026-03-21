package io.github.ih0rd.polyglot.model.types;

import java.util.List;

/**
 * Union of multiple possible variants.
 *
 * @param variants allowed variants
 */
public record PolyUnion(List<PolyType> variants) implements PolyType {
  public PolyUnion {
    variants = List.copyOf(variants);
  }
}
