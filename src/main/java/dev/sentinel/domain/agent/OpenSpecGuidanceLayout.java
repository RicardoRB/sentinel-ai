package dev.sentinel.domain.agent;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where Sentinel-owned OpenSpec guidance lives for each supported agent, and where OpenSpec puts
 * that agent's official verify workflow. Paths are project-relative; this type performs no I/O.
 */
public record OpenSpecGuidanceLayout(
    String agentId, String guidanceFile, List<String> verifyWorkflowFiles) {
  public static final String SKILL_NAME = "sentinel-openspec-workflow";
  public static final String OWNERSHIP_MARKER = "<!-- sentinel-owned: openspec-guidance v1 -->";

  private static final Map<String, OpenSpecGuidanceLayout> LAYOUTS =
      Map.of(
          "claude-code",
          new OpenSpecGuidanceLayout(
              "claude-code",
              ".claude/skills/" + SKILL_NAME + "/SKILL.md",
              List.of(
                  ".claude/commands/opsx/verify.md",
                  ".claude/skills/openspec-verify-change/SKILL.md")),
          "opencode",
          new OpenSpecGuidanceLayout(
              "opencode",
              ".opencode/skills/" + SKILL_NAME + "/SKILL.md",
              List.of(
                  ".opencode/commands/opsx-verify.md",
                  ".opencode/skills/openspec-verify-change/SKILL.md")));

  public OpenSpecGuidanceLayout {
    verifyWorkflowFiles = List.copyOf(verifyWorkflowFiles);
  }

  public static Optional<OpenSpecGuidanceLayout> forAgent(final String agentId) {
    return Optional.ofNullable(LAYOUTS.get(agentId));
  }

  public static List<OpenSpecGuidanceLayout> all() {
    return List.of(LAYOUTS.get("claude-code"), LAYOUTS.get("opencode"));
  }
}
