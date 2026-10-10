package dev.sentinel.infrastructure.cli.init;

import dev.sentinel.domain.config.SentinelException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.stream.IntStream;

/** Small raw-key multiple-selection menu shared by the init wizard. */
public final class MultiSelectMenu {
  private static final int CTRL_C = 3;
  private static final int ESCAPE = 27;
  private static final int NO_DIRECTION = -1;

  public List<Integer> select(
      final String title,
      final List<String> labels,
      final IntSupplier keys,
      final PrintWriter out) {
    return select(title, labels, "Select at least one item.", keys, out);
  }

  public List<Integer> select(
      final String title,
      final List<String> labels,
      final String emptySelectionMessage,
      final IntSupplier keys,
      final PrintWriter out) {
    return select(title, labels, emptySelectionMessage, keys, out, Set.of());
  }

  public List<Integer> select(
      final String title,
      final List<String> labels,
      final String emptySelectionMessage,
      final IntSupplier keys,
      final PrintWriter out,
      final Set<Integer> initialSelections) {
    if (labels.isEmpty()) {
      throw new IllegalArgumentException("A menu must have at least one item");
    }
    final boolean[] selected = new boolean[labels.size()];
    initialSelections.stream()
        .filter(index -> index >= 0 && index < selected.length)
        .forEach(index -> selected[index] = true);
    int cursor = 0;
    render(title, labels, selected, cursor, out);
    while (true) {
      final int key = keys.getAsInt();
      switch (key) {
        case ' ' -> selected[cursor] = !selected[cursor];
        case '\n', '\r' -> {
          return confirmed(selected, emptySelectionMessage);
        }
        case ESCAPE -> cursor = moveCursor(cursor, labels.size(), keys);
        case 'q', 'Q', CTRL_C ->
            throw new SentinelException("Initialization cancelled by the user.");
        default -> {
          if (key < 0) {
            throw new SentinelException(
                "Initialization cancelled: input ended before setup completed.");
          }
        }
      }
      render(title, labels, selected, cursor, out);
    }
  }

  private static List<Integer> confirmed(
      final boolean[] selected, final String emptySelectionMessage) {
    final List<Integer> result =
        IntStream.range(0, selected.length).filter(i -> selected[i]).boxed().toList();
    if (result.isEmpty()) {
      throw new SentinelException(emptySelectionMessage);
    }
    return result;
  }

  private static int moveCursor(final int cursor, final int size, final IntSupplier keys) {
    final int bracket = keys.getAsInt();
    final int direction = bracket == '[' ? keys.getAsInt() : NO_DIRECTION;
    return switch (direction) {
      case 'A' -> (cursor + size - 1) % size;
      case 'B' -> (cursor + 1) % size;
      default -> cursor;
    };
  }

  private void render(
      final String title,
      final List<String> labels,
      final boolean[] selected,
      final int cursor,
      final PrintWriter out) {
    out.print("\033[2J\033[H");
    out.println(title + " (Space toggles, arrows move, Enter confirms, q cancels):");
    for (int i = 0; i < labels.size(); i++) {
      out.printf(
          "%s %s %d) %s%n",
          i == cursor ? ">" : " ", selected[i] ? "[x]" : "[ ]", i + 1, labels.get(i));
    }
    out.flush();
  }
}
