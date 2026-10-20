package dev.sentinel.domain.agent;

/** Normalized result that a native harness protocol may serialize. */
public record NativeHookOutcome(Kind kind, String message) {
  /** How the harness should continue after the hook. */
  public enum Kind {
    ALLOW,
    DENY,
    FEEDBACK,
    ERROR
  }

  /** Lets the tool call proceed. */
  public static NativeHookOutcome allow() {
    return new NativeHookOutcome(Kind.ALLOW, "");
  }

  /** Blocks the tool call for {@code reason}. */
  public static NativeHookOutcome deny(final String reason) {
    return new NativeHookOutcome(Kind.DENY, reason);
  }

  /** Returns {@code message} to the agent as feedback to act on. */
  public static NativeHookOutcome feedback(final String message) {
    return new NativeHookOutcome(Kind.FEEDBACK, message);
  }

  /** Reports that the hook itself failed with {@code message}. */
  public static NativeHookOutcome error(final String message) {
    return new NativeHookOutcome(Kind.ERROR, message);
  }
}
