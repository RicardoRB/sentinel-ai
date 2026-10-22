package dev.ward.domain.agent;

/**
 * Harness-neutral event data extracted from a native agent hook payload. {@code continued} marks a
 * turn end that the harness is already continuing because of an earlier hook decision.
 */
public record NativeHookInput(
    String eventName,
    String toolName,
    String workingDirectory,
    String path,
    String command,
    String patch,
    String skill,
    boolean unreadable,
    boolean continued) {

  /** An event whose payload could not be parsed. */
  public static NativeHookInput unreadableInput() {
    return new NativeHookInput(null, null, "", null, null, null, null, true, false);
  }

  /** An event without payload, used when the hook reads nothing from stdin. */
  public static NativeHookInput empty() {
    return new NativeHookInput(null, null, "", null, null, null, null, false, false);
  }
}
