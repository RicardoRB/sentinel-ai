package dev.sentinel;

import java.util.List;
import java.util.Map;
import org.apache.fory.json.ForyJson;

/** Read-only navigation over JSON parsed by Fory into maps, lists and scalars, for assertions. */
public final class JsonTree extends JsonTreeValue {
  private static final ForyJson JSON = ForyJson.builder().build();

  private JsonTree(final Object value) {
    super(value);
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
}
