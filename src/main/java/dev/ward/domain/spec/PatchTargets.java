package dev.ward.domain.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Extracts every path a patch would affect. Understands the {@code *** Begin Patch} format used by
 * OpenCode and Codex and unified or git diffs. Text with no recognizable header is uninspectable.
 */
public final class PatchTargets {
  private static final String ADD = "*** Add File:";
  private static final String UPDATE = "*** Update File:";
  private static final String DELETE = "*** Delete File:";
  private static final String MOVE = "*** Move to:";
  private static final String OLD_FILE = "--- ";
  private static final String NEW_FILE = "+++ ";
  private static final String RENAME_FROM = "rename from ";
  private static final String RENAME_TO = "rename to ";
  private static final String COPY_TO = "copy to ";
  private static final String NO_FILE = "/dev/null";
  private static final char QUOTE = '"';

  private PatchTargets() {}

  /** Returns the patch targets in order, or empty when the patch cannot be inspected. */
  public static Optional<List<GuardTarget>> parse(final String patch) {
    if (patch == null || patch.isBlank()) {
      return Optional.of(List.of());
    }
    final Parser parser = new Parser(patch.split("\\R", -1));
    parser.run();
    return parser.recognized ? Optional.of(List.copyOf(parser.targets)) : Optional.empty();
  }

  /** Line-oriented parser state for one {@link #parse} call. */
  private static final class Parser {
    private final String[] lines;
    private final List<GuardTarget> targets = new ArrayList<>();
    private boolean recognized;
    private String updated;

    Parser(final String... lines) {
      this.lines = lines.clone();
    }

    void run() {
      int index = 0;
      while (index < lines.length) {
        final String line = lines[index];
        final boolean diffHeader =
            line.startsWith(OLD_FILE)
                && index + 1 < lines.length
                && lines[index + 1].startsWith(NEW_FILE);
        if (diffHeader) {
          diff(line, lines[index + 1]);
          index += 2;
        } else {
          if (!line.startsWith(OLD_FILE)) {
            header(line);
          }
          index++;
        }
      }
    }

    private void header(final String line) {
      if (line.startsWith(ADD)) {
        add(GuardOperation.WRITE, value(line, ADD));
      } else if (line.startsWith(UPDATE)) {
        updated = value(line, UPDATE);
        add(GuardOperation.WRITE, updated);
      } else if (line.startsWith(DELETE)) {
        add(GuardOperation.DELETE, value(line, DELETE));
      } else if (line.startsWith(MOVE)) {
        if (updated != null) {
          add(GuardOperation.MOVE_FROM, updated);
        }
        add(GuardOperation.MOVE_TO, value(line, MOVE));
      } else if (line.startsWith(RENAME_FROM)) {
        add(GuardOperation.MOVE_FROM, unquote(line.substring(RENAME_FROM.length())));
      } else if (line.startsWith(RENAME_TO)) {
        add(GuardOperation.MOVE_TO, unquote(line.substring(RENAME_TO.length())));
      } else if (line.startsWith(COPY_TO)) {
        add(GuardOperation.WRITE, unquote(line.substring(COPY_TO.length())));
      }
    }

    private void diff(final String oldLine, final String newLine) {
      recognized = true;
      final String before = diffPath(oldLine.substring(OLD_FILE.length()));
      final String after = diffPath(newLine.substring(NEW_FILE.length()));
      if (before == null && after != null) {
        add(GuardOperation.WRITE, after);
      } else if (before != null && after == null) {
        add(GuardOperation.DELETE, before);
      } else if (before != null && before.equals(after)) {
        add(GuardOperation.WRITE, after);
      } else if (before != null) {
        add(GuardOperation.MOVE_FROM, before);
        add(GuardOperation.MOVE_TO, after);
      }
    }

    private void add(final GuardOperation operation, final String path) {
      recognized = true;
      if (!path.isEmpty()) {
        targets.add(GuardTarget.of(operation, path));
      }
    }

    private static String value(final String line, final String prefix) {
      return line.substring(prefix.length()).strip();
    }

    /** Returns the diff header path without timestamp or {@code a/}/{@code b/} prefix. */
    private static String diffPath(final String header) {
      final int tab = header.indexOf('\t');
      final String path = unquote(tab < 0 ? header : header.substring(0, tab));
      if (NO_FILE.equals(path)) {
        return null;
      }
      return path.startsWith("a/") || path.startsWith("b/") ? path.substring(2) : path;
    }

    private static String unquote(final String text) {
      final String value = text.strip();
      return value.length() > 1 && value.charAt(0) == QUOTE && value.endsWith("\"")
          ? value.substring(1, value.length() - 1)
          : value;
    }
  }
}
