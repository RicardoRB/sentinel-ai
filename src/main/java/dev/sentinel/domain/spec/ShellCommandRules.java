package dev.sentinel.domain.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Classifies what one simple command does to the paths it names. */
final class ShellCommandRules {
  private static final Set<String> READ_ONLY =
      Set.of(
          "cat",
          "less",
          "more",
          "head",
          "tail",
          "grep",
          "egrep",
          "fgrep",
          "zgrep",
          "rg",
          "ag",
          "ack",
          "ls",
          "tree",
          "wc",
          "diff",
          "cmp",
          "stat",
          "file",
          "realpath",
          "readlink",
          "echo",
          "printf",
          "test",
          "[",
          "[[",
          "true",
          "false",
          "pwd",
          "which",
          "type",
          "whereis",
          "basename",
          "dirname",
          "du",
          "df",
          "uniq",
          "cut",
          "tr",
          "jq",
          "yq",
          "column",
          "nl",
          "od",
          "hexdump",
          "strings",
          "md5",
          "md5sum",
          "shasum",
          "sha1sum",
          "sha256sum",
          "date",
          "sleep",
          "bat",
          "glow",
          "popd",
          "exit",
          "return",
          "export",
          "set",
          "unset",
          "{",
          "}");
  private static final Set<String> DELETES = Set.of("rm", "rmdir", "unlink", "shred");
  private static final Set<String> COPIES = Set.of("cp", "install", "ln", "rsync");
  private static final Set<String> SHELLS = Set.of("sh", "bash", "zsh", "dash", "ksh");
  private static final String MOVE = "mv";
  private static final String EVAL = "eval";
  private static final String RSYNC = "rsync";
  private static final String TARGET_DIRECTORY = "-t";
  private static final String TARGET_DIRECTORY_LONG = "--target-directory=";

  private ShellCommandRules() {}

  static void classify(
      final String name, final List<ShellWord> arguments, final ShellFindings findings) {
    if (READ_ONLY.contains(name)) {
      return;
    }
    if (DELETES.contains(name)) {
      all(arguments, GuardOperation.DELETE, findings);
    } else if (MOVE.equals(name)) {
      move(arguments, findings);
    } else if (COPIES.contains(name)) {
      copy(name, arguments, findings);
    } else if (SHELLS.contains(name)) {
      shell(arguments, findings);
    } else if (EVAL.equals(name)) {
      findings.nested(String.join(" ", arguments.stream().map(ShellWord::text).toList()));
    } else if (!ShellToolRules.classify(name, arguments, findings)) {
      all(arguments, GuardOperation.WRITE, findings);
    }
  }

  /** Treats every argument, and every {@code option=value} value, as a target. */
  static void all(
      final List<ShellWord> arguments,
      final GuardOperation operation,
      final ShellFindings findings) {
    for (final ShellWord argument : arguments) {
      final String text = argument.text();
      final int equals = text.indexOf('=');
      if (!text.startsWith("-")) {
        findings.target(operation, argument);
      }
      if (equals >= 0) {
        findings.target(operation, argument.withText(text.substring(equals + 1)));
      }
    }
  }

  /** Moves every source and writes the destination, honoring {@code -t}. */
  static void move(final List<ShellWord> arguments, final ShellFindings findings) {
    final Split split = Split.of(arguments);
    split.sources().forEach(source -> findings.target(GuardOperation.MOVE_FROM, source));
    if (split.destination() != null) {
      findings.target(GuardOperation.MOVE_TO, split.destination());
    }
  }

  private static void copy(
      final String name, final List<ShellWord> arguments, final ShellFindings findings) {
    final boolean rsync = RSYNC.equals(name);
    final boolean deletes =
        rsync && arguments.stream().anyMatch(argument -> argument.text().startsWith("--delete"));
    final Split split = Split.of(arguments);
    if (rsync && arguments.stream().anyMatch(a -> "--remove-source-files".equals(a.text()))) {
      split.sources().forEach(source -> findings.target(GuardOperation.DELETE, source));
    }
    if (split.destination() != null) {
      findings.target(deletes ? GuardOperation.DELETE : GuardOperation.WRITE, split.destination());
    }
  }

  private static void shell(final List<ShellWord> arguments, final ShellFindings findings) {
    for (int index = 0; index < arguments.size(); index++) {
      final String text = arguments.get(index).text();
      if (text.startsWith("-") && !text.startsWith("--") && text.indexOf('c') > 0) {
        if (index + 1 < arguments.size()) {
          findings.nested(arguments.get(index + 1).text());
        }
        return;
      }
    }
    all(arguments, GuardOperation.WRITE, findings);
  }

  /** Sources and destination of a copy or move. */
  private record Split(List<ShellWord> sources, ShellWord destination) {

    static Split of(final List<ShellWord> arguments) {
      ShellWord directory = null;
      final List<ShellWord> operands = new ArrayList<>();
      int index = 0;
      while (index < arguments.size()) {
        final ShellWord argument = arguments.get(index);
        final String text = argument.text();
        if (TARGET_DIRECTORY.equals(text) && index + 1 < arguments.size()) {
          directory = arguments.get(index + 1);
          index++;
        } else if (text.startsWith(TARGET_DIRECTORY_LONG)) {
          directory = argument.withText(text.substring(TARGET_DIRECTORY_LONG.length()));
        } else if (!text.startsWith("-")) {
          operands.add(argument);
        }
        index++;
      }
      if (directory != null) {
        return new Split(operands, directory);
      }
      return operands.isEmpty()
          ? new Split(List.of(), null)
          : new Split(operands.subList(0, operands.size() - 1), operands.getLast());
    }
  }
}
