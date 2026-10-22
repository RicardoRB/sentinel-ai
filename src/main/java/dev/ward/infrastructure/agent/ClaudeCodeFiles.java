package dev.ward.infrastructure.agent;

import dev.ward.domain.config.WardException;
import dev.ward.domain.project.Project;
import java.util.Set;

/**
 * The Claude Code files and hook commands of one Ward project. A root project keeps the original
 * names and scripts; a nested project, installed at the repository root, gets files suffixed with
 * its path slug whose scripts point Ward at the project directory.
 *
 * @param projectPath the {@code /}-separated project path below the install root, or empty
 */
record ClaudeCodeFiles(String projectPath) {
  static final String HOOK_FILE = ".claude/hooks/ward-edit-write";
  static final String GUARD_FILE = ".claude/hooks/ward-openspec-guard";
  private static final String PROJECT_DIR = "${CLAUDE_PROJECT_DIR}/";
  private static final String SESSION_PROJECT = "-C \"${CLAUDE_PROJECT_DIR:-.}\"";

  /**
   * Validates the path.
   *
   * @throws WardException if the path cannot be written safely inside single quotes
   */
  ClaudeCodeFiles {
    if (projectPath.contains("'") || projectPath.contains("\n") || projectPath.contains("\r")) {
      throw new WardException(
          "Cannot install the Claude Code integration: the project path '"
              + projectPath.strip()
              + "' contains a quote or line break.");
    }
  }

  private String suffix() {
    return projectPath.isEmpty() ? "" : "-" + Project.slug(projectPath);
  }

  String hookFile() {
    return HOOK_FILE + suffix();
  }

  String guardFile() {
    return GUARD_FILE + suffix();
  }

  /** The hook commands this project registers, which are the only ones it may replace. */
  Set<String> commands() {
    if (projectPath.isEmpty()) {
      return Set.of(HOOK_FILE, GUARD_FILE, PROJECT_DIR + HOOK_FILE, PROJECT_DIR + GUARD_FILE);
    }
    return Set.of(PROJECT_DIR + hookFile(), PROJECT_DIR + guardFile());
  }

  /** Points a settings template at this project's hook files. */
  String settings(final String template) {
    return template
        .replace(PROJECT_DIR + HOOK_FILE + "\"", PROJECT_DIR + hookFile() + "\"")
        .replace(PROJECT_DIR + GUARD_FILE + "\"", PROJECT_DIR + guardFile() + "\"");
  }

  /**
   * Points a hook script at this project. A nested project's script changes to the repository root
   * it lives under, two levels up, and passes the project path, so the session's start directory
   * does not matter.
   */
  String script(final String template) {
    if (projectPath.isEmpty()) {
      return template;
    }
    return template
        .replace("exec ward ", "cd \"$(dirname \"$0\")/../..\" || exit 2\nexec ward ")
        .replace(SESSION_PROJECT, "-C '" + projectPath + "'");
  }
}
