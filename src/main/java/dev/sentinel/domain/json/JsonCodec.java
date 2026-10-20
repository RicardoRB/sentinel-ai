package dev.sentinel.domain.json;

/**
 * Port for JSON encoding and decoding.
 *
 * <p>Adapters couple a JSON library to this port so that application and CLI code depend only on
 * the contract. Implementations must not throw checked exceptions; serialization failures are
 * reported as runtime exceptions, and undecodable input as {@link JsonCodecException}.
 */
public interface JsonCodec {
  /** Serializes {@code value} as compact JSON. */
  String toJson(Object value);

  /** Serializes {@code value} as indented JSON. */
  String toPrettyJson(Object value);

  /**
   * Parses {@code json} into an instance of {@code type}.
   *
   * @throws JsonCodecException if the JSON is invalid or does not match the type
   */
  <T> T fromJson(String json, Class<T> type);
}
