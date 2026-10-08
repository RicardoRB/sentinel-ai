package dev.sentinel.infrastructure.json;

import dev.sentinel.domain.json.JsonCodec;
import javax.inject.Inject;
import org.apache.fory.json.ForyJson;

/** {@link JsonCodec} backed by Fory. */
public final class ForyJsonCodec implements JsonCodec {
  private final ForyJson json;

  @Inject
  public ForyJsonCodec() {
    json = ForyJson.builder().build();
  }

  @Override
  public String toJson(Object value) {
    return json.toJson(value);
  }

  @Override
  public String toPrettyJson(Object value) {
    return json.toPrettyJson(value);
  }

  @Override
  public <T> T fromJson(String source, Class<T> type) {
    return json.fromJson(source, type);
  }
}
