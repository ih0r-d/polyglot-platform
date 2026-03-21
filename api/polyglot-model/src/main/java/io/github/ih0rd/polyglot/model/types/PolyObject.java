package io.github.ih0rd.polyglot.model.types;

import java.util.Map;

/**
 * Object-like type with named fields.
 *
 * @param fields field names and their types
 */
public record PolyObject(Map<String, PolyType> fields) implements PolyType {
  public PolyObject {
    fields = Map.copyOf(fields);
  }
}
