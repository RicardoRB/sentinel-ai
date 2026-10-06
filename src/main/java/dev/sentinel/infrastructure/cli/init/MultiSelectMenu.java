package dev.sentinel.infrastructure.cli.init;

import dev.sentinel.domain.config.SentinelException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/** Small raw-key multiple-selection menu shared by the init wizard. */
public final class MultiSelectMenu {
  public List<Integer> select(
      String title, List<String> labels, IntSupplier keys, PrintWriter out) {
    return select(title, labels, "Select at least one item.", keys, out);
  }

  public List<Integer> select(
      String title,
      List<String> labels,
      String emptySelectionMessage,
      IntSupplier keys,
      PrintWriter out) {
    if (labels.isEmpty()) throw new IllegalArgumentException("A menu must have at least one item");
    boolean[] selected = new boolean[labels.size()];
    int cursor = 0;
    render(title, labels, selected, cursor, out);
    while (true) {
      int key = keys.getAsInt();
      if (key < 0)
        throw new SentinelException(
            "Initialization cancelled: input ended before setup completed.");
      if (key == 'q' || key == 'Q' || key == 3)
        throw new SentinelException("Initialization cancelled by the user.");
      if (key == ' ') selected[cursor] = !selected[cursor];
      else if (key == '\n' || key == '\r') {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < selected.length; i++) if (selected[i]) result.add(i);
        if (result.isEmpty()) throw new SentinelException(emptySelectionMessage);
        return result;
      } else if (key == 27) {
        int bracket = keys.getAsInt();
        int direction = bracket == '[' ? keys.getAsInt() : -1;
        if (direction == 'A') cursor = (cursor + labels.size() - 1) % labels.size();
        if (direction == 'B') cursor = (cursor + 1) % labels.size();
      }
      render(title, labels, selected, cursor, out);
    }
  }

  private void render(
      String title, List<String> labels, boolean[] selected, int cursor, PrintWriter out) {
    out.print("\033[2J\033[H");
    out.println(title + " (Space toggles, arrows move, Enter confirms, q cancels):");
    for (int i = 0; i < labels.size(); i++)
      out.printf(
          "%s %s %d) %s%n",
          i == cursor ? ">" : " ", selected[i] ? "[x]" : "[ ]", i + 1, labels.get(i));
    out.flush();
  }
}
