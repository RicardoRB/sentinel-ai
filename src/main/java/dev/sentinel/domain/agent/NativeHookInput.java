package dev.sentinel.domain.agent;

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
  public NativeHookInput(
      final String eventName,
      final String toolName,
      final String workingDirectory,
      final String path,
      final String command,
      final String patch,
      final String skill,
      final boolean unreadable) {
    this(eventName, toolName, workingDirectory, path, command, patch, skill, unreadable, false);
  }

  public String eventName() {
    return eventName;
  }

  public String toolName() {
    return toolName;
  }

  public static NativeHookInput unreadableInput() {
    return new NativeHookInput(null, null, "", null, null, null, null, true);
  }

  public static NativeHookInput empty() {
    return new NativeHookInput(null, null, "", null, null, null, null, false);
  }
}
