package dev.sentinel.domain.agent;

/** Normalized result that a native harness protocol may serialize. */
public record NativeHookOutcome(Kind kind, String message) {
  public enum Kind {
    ALLOW,
    DENY,
    FEEDBACK,
    ERROR
  }

  public static NativeHookOutcome allow() {
    return new NativeHookOutcome(Kind.ALLOW, "");
  }

  public static NativeHookOutcome deny(final String reason) {
    return new NativeHookOutcome(Kind.DENY, reason);
  }

  public static NativeHookOutcome feedback(final String message) {
    return new NativeHookOutcome(Kind.FEEDBACK, message);
  }

  public static NativeHookOutcome error(final String message) {
    return new NativeHookOutcome(Kind.ERROR, message);
  }
}
