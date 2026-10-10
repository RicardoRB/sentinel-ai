package dev.sentinel.domain.json;

/** Reports JSON that a {@link JsonCodec} cannot decode. */
public class JsonCodecException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public JsonCodecException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
