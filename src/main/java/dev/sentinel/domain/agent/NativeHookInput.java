package dev.sentinel.domain.agent;

/** Harness-neutral event data extracted from a native agent hook payload. */
public record NativeHookInput(
    String eventName,
    String toolName,
    String workingDirectory,
    String path,
    String command,
    String patch,
    String skill,
    boolean unreadable) {
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
