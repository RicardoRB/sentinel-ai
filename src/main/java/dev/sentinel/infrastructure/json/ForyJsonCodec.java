package dev.sentinel.infrastructure.json;

import dev.sentinel.domain.json.JsonCodec;
import dev.sentinel.domain.json.JsonCodecException;
import javax.inject.Inject;
import org.apache.fory.exception.ForyException;
import org.apache.fory.json.ForyJson;

/** {@link JsonCodec} backed by Fory. */
public final class ForyJsonCodec implements JsonCodec {
  private final ForyJson json;

  @Inject
  public ForyJsonCodec() {
    json = ForyJson.builder().build();
  }

  @Override
  public String toJson(final Object value) {
    return json.toJson(value);
  }

  @Override
  public String toPrettyJson(final Object value) {
    return json.toPrettyJson(value);
  }

  @Override
  public <T> T fromJson(final String source, final Class<T> type) {
    try {
      return json.fromJson(source, type);
    } catch (ForyException exception) {
      throw new JsonCodecException("Invalid JSON: " + exception.getMessage(), exception);
    }
  }
}
