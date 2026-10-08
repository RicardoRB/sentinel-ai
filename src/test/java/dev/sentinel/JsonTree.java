package dev.sentinel;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.fory.json.ForyJson;

/** Read-only navigation over JSON parsed by Fory into maps, lists and scalars, for assertions. */
public final class JsonTree {
  private static final ForyJson JSON = ForyJson.builder().build();

  private final Object value;

  private JsonTree(final Object value) {
    this.value = value;
  }

  /** Parses a complete JSON document; fails on malformed input or trailing text. */
  public static JsonTree parse(final String json) {
    return new JsonTree(JSON.fromJson(json, Object.class));
  }

  public JsonTree get(final String field) {
    final Map<?, ?> object = as(Map.class);
    if (!object.containsKey(field)) {
      throw new AssertionError("Missing JSON field '" + field + "' in " + value);
    }
    return new JsonTree(object.get(field));
  }

  public JsonTree get(final int index) {
    return new JsonTree(as(List.class).get(index));
  }

  public boolean has(final String field) {
    return value instanceof Map<?, ?> object && object.containsKey(field);
  }

  public int size() {
    return value instanceof Map<?, ?> object ? object.size() : as(List.class).size();
  }

  public String asString() {
    return as(String.class);
  }

  public int asInt() {
    return Math.toIntExact(asLong());
  }

  public long asLong() {
    return as(Number.class).longValue();
  }

  public boolean asBoolean() {
    return as(Boolean.class);
  }

  public Object value() {
    return value;
  }

  private <T> T as(final Class<T> type) {
    if (!type.isInstance(value)) {
      throw new AssertionError(
          "Expected JSON " + type.getSimpleName() + " but was " + Objects.toString(value));
    }
    return type.cast(value);
  }

  @Override
  public boolean equals(final Object other) {
    return other instanceof JsonTree tree && Objects.equals(value, tree.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }

  @Override
  public String toString() {
    return Objects.toString(value);
  }
}
