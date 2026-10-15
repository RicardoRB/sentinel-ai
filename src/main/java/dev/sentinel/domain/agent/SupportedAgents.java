package dev.sentinel.domain.agent;

import java.util.List;
import java.util.stream.Collectors;

/** Canonical agent integration identifiers and display names. */
public final class SupportedAgents {
  private static final List<Definition> DEFINITIONS =
      List.of(
          new Definition("opencode", "OpenCode"),
          new Definition("claude-code", "Claude Code"),
          new Definition("codex", "OpenAI Codex CLI"),
          new Definition("cursor", "Cursor"),
          new Definition("copilot", "GitHub Copilot CLI"),
          new Definition("gemini", "Gemini CLI"),
          new Definition("windsurf", "Windsurf"),
          new Definition("cline", "Cline"),
          new Definition("factory-droid", "Factory Droid"),
          new Definition("amp", "Amp"));

  public record Definition(String id, String label) {}

  private SupportedAgents() {}

  public static List<Definition> definitions() {
    return DEFINITIONS;
  }

  public static List<String> ids() {
    return DEFINITIONS.stream().map(Definition::id).toList();
  }

  public static String idsText() {
    return DEFINITIONS.stream().map(Definition::id).collect(Collectors.joining(", "));
  }
}
