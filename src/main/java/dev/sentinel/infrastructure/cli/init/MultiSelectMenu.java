package dev.sentinel.infrastructure.cli.init;

import dev.sentinel.domain.config.SentinelException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.stream.IntStream;

/** Small raw-key multiple-selection menu shared by the init wizard. */
public final class MultiSelectMenu {
  private static final int CTRL_C = 3;
  private static final int ESCAPE = 27;
  private static final int NO_DIRECTION = -1;

  /** Runs a menu that requires at least one ticked row. */
  public List<Integer> select(
      final String title,
      final List<String> labels,
      final IntSupplier keys,
      final PrintWriter out) {
    return select(title, labels, "Select at least one item.", keys, out);
  }

  /** Runs a menu that requires a ticked row and shows {@code emptySelectionMessage} otherwise. */
  public List<Integer> select(
      final String title,
      final List<String> labels,
      final String emptySelectionMessage,
      final IntSupplier keys,
      final PrintWriter out) {
    return select(new MenuSpec(title, labels, emptySelectionMessage, Set.of(), false), keys, out);
  }

  /**
   * Runs the menu until Enter confirms. An empty confirmation is returned when {@code
   * spec.allowEmpty()}; otherwise it shows {@code spec.emptySelectionMessage()} and keeps the menu
   * open, cursor unchanged.
   *
   * @throws IllegalArgumentException if the menu has no rows
   */
  public List<Integer> select(final MenuSpec spec, final IntSupplier keys, final PrintWriter out) {
    final String title = spec.title();
    final List<String> labels = spec.labels();
    if (labels.isEmpty()) {
      throw new IllegalArgumentException("A menu must have at least one item");
    }
    final MenuState menu = new MenuState(labels.size(), spec.initialSelections());
    render(title, labels, menu, out, "");
    while (true) {
      final Optional<List<Integer>> confirmed = menu.press(keys.getAsInt(), keys);
      if (confirmed.isPresent() && (spec.allowEmpty() || !confirmed.get().isEmpty())) {
        return confirmed.get();
      }
      render(title, labels, menu, out, confirmed.isPresent() ? spec.emptySelectionMessage() : "");
    }
  }

  private void render(
      final String title,
      final List<String> labels,
      final MenuState menu,
      final PrintWriter out,
      final String message) {
    if (menu.renderedLines > 0) {
      // Move back to the menu's first line and clear only what the menu printed.
      out.print("\033[" + menu.renderedLines + "F\033[J");
    }
    out.println(title + " (Space toggles, arrows move, Enter confirms, q cancels):");
    for (int i = 0; i < labels.size(); i++) {
      out.printf(
          "%s %s %d) %s%n",
          i == menu.cursor ? ">" : " ", menu.selected[i] ? "[x]" : "[ ]", i + 1, labels.get(i));
    }
    if (!message.isEmpty()) {
      out.println(message);
    }
    menu.renderedLines = 1 + labels.size() + (message.isEmpty() ? 0 : 1);
    out.flush();
  }

  /** Cursor and selection state of one menu. */
  private static final class MenuState {
    private final boolean[] selected;
    private int cursor;
    private int renderedLines;

    MenuState(final int size, final Set<Integer> initialSelections) {
      selected = new boolean[size];
      initialSelections.stream().filter(index -> index >= 0 && index < size).forEach(this::toggle);
    }

    /** Applies one key; returns the selected rows when the key confirms the menu. */
    Optional<List<Integer>> press(final int key, final IntSupplier keys) {
      requireNotCancelled(key);
      switch (key) {
        case ' ' -> toggle(cursor);
        case '\n', '\r' -> {
          return Optional.of(
              IntStream.range(0, selected.length).filter(i -> selected[i]).boxed().toList());
        }
        case ESCAPE -> move(keys);
        default -> {
          // Other keys are ignored.
        }
      }
      return Optional.empty();
    }

    private static void requireNotCancelled(final int key) {
      if (key < 0) {
        throw new SentinelException(
            "Initialization cancelled: input ended before setup completed.");
      }
      switch (key) {
        case 'q', 'Q', CTRL_C ->
            throw new SentinelException("Initialization cancelled by the user.");
        default -> {
          // Not a cancellation key.
        }
      }
    }

    private void toggle(final int index) {
      selected[index] = !selected[index];
    }

    private void move(final IntSupplier keys) {
      final int bracket = keys.getAsInt();
      final int direction = bracket == '[' ? keys.getAsInt() : NO_DIRECTION;
      switch (direction) {
        case 'A' -> {
          cursor = (cursor + selected.length - 1) % selected.length;
        }
        case 'B' -> {
          cursor = (cursor + 1) % selected.length;
        }
        default -> {
          // Other escape sequences leave the cursor where it is.
        }
      }
    }
  }
}
