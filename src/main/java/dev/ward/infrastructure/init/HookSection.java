package dev.ward.infrastructure.init;

import dev.ward.domain.config.WardException;

/**
 * The Ward section one project owns in a Git hook. A root project keeps the original unkeyed
 * markers and body; a nested project's markers carry its repository-relative path, and its body
 * runs from the project directory only when the project changed.
 *
 * @param projectPath the {@code /}-separated project path, empty for a root project
 */
record HookSection(String projectPath) {
  static final String START_MARKER = "# ward-owned: quality-hooks v1 begin";
  static final String END_MARKER = "# ward-owned: quality-hooks v1 end";
  private static final String PRE_PUSH = "pre-push";

  /**
   * Creates the section.
   *
   * @throws WardException if the path cannot be written safely inside single quotes
   */
  HookSection {
    if (projectPath.contains("'") || projectPath.contains("\n") || projectPath.contains("\r")) {
      throw new WardException(
          "Cannot install quality hooks: the project path '"
              + projectPath.strip()
              + "' contains a quote or line break.");
    }
  }

  String start() {
    return projectPath.isEmpty() ? START_MARKER : START_MARKER + " " + projectPath;
  }

  String end() {
    return projectPath.isEmpty() ? END_MARKER : END_MARKER + " " + projectPath;
  }

  /** Returns the full section, markers included, that runs {@code profile}. */
  String render(final String profile) {
    if (projectPath.isEmpty()) {
      return start()
          + "\nif ! ward check --profile "
          + profile
          + " --format json; then\n  exit 1\nfi\n"
          + end()
          + "\n";
    }
    final String quoted = "'" + projectPath + "'";
    return start()
        + "\nif "
        + changed(profile, quoted)
        + "; then\n"
        + "  if ! (cd \"$(git rev-parse --show-toplevel)\"/"
        + quoted
        + " && ward check --profile "
        + profile
        + " --format json); then\n"
        + "    exit 1\n"
        + "  fi\n"
        + "fi\n"
        + end()
        + "\n";
  }

  /**
   * The condition under which the project changed. Pre-push compares with the upstream instead of
   * reading the ref list on stdin, which user hook content after this section may still need.
   */
  private static String changed(final String profile, final String quoted) {
    if (PRE_PUSH.equals(profile)) {
      return "! git rev-parse --verify --quiet '@{upstream}' >/dev/null"
          + " || ! git diff --quiet '@{upstream}...HEAD' -- "
          + quoted;
    }
    return "! git diff --cached --quiet -- " + quoted;
  }
}
