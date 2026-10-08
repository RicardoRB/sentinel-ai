package dev.sentinel.domain.json;

/**
 * Port for JSON encoding and decoding.
 *
 * <p>Adapters couple a JSON library to this port so that application and CLI code depend only on
 * the contract. Implementations must not throw checked exceptions; serialization failures are
 * reported as runtime exceptions.
 */
public interface JsonCodec {
  String toJson(Object value);

  String toPrettyJson(Object value);

  <T> T fromJson(String json, Class<T> type);
}
