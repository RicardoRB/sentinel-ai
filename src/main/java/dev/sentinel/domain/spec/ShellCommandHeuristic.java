package dev.sentinel.domain.spec;

import java.util.List;
import java.util.Optional;

/**
 * Best-effort interpretation of an agent's shell command. It recognizes common file-changing
 * commands, redirections, and {@code openspec archive}. It is a heuristic, not a security boundary:
 * scripts, expansions, and other indirection that do not name a path in the command text are not
 * detected.
 */
public final class ShellCommandHeuristic {
  private static final String CHANGE_DIRECTORY = "cd";
  private static final String PUSH_DIRECTORY = "pushd";
  private static final ShellWord UNKNOWN_DIRECTORY = new ShellWord("", false, true);

  private ShellCommandHeuristic() {}

  public static ShellInspection inspect(final String command) {
    return inspect(command, 0);
  }

  static ShellInspection inspect(final String command, final int depth) {
    if (command == null || command.isBlank()) {
      return new ShellInspection(true, false, List.of());
    }
    final Optional<List<ShellSegment>> segments = ShellLexer.lex(command);
    if (segments.isEmpty()) {
      return ShellInspection.uninspectable();
    }
    final ShellFindings findings = new ShellFindings(depth);
    for (final ShellSegment segment : segments.get()) {
      segment.writes().forEach(target -> findings.target(GuardOperation.WRITE, target));
      final List<ShellWord> words = ShellWrappers.unwrap(segment.words());
      if (words.isEmpty()) {
        continue;
      }
      final String name = ShellWrappers.executable(words.getFirst().text());
      if (CHANGE_DIRECTORY.equals(name) || PUSH_DIRECTORY.equals(name)) {
        words.stream()
            .skip(1)
            .filter(word -> !word.text().startsWith("-"))
            .findFirst()
            .ifPresentOrElse(
                findings::changeDirectory, () -> findings.changeDirectory(UNKNOWN_DIRECTORY));
      } else {
        ShellCommandRules.classify(name, words.subList(1, words.size()), findings);
      }
    }
    return findings.result();
  }
}
