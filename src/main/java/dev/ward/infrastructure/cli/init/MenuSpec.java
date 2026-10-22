package dev.ward.infrastructure.cli.init;

import java.util.List;
import java.util.Set;

/**
 * What a multiple-selection menu shows and accepts.
 *
 * @param title the line above the rows
 * @param labels one label per row, in display order
 * @param emptySelectionMessage shown when Enter confirms nothing and empty is not allowed
 * @param initialSelections zero-based rows ticked when the menu opens
 * @param allowEmpty whether confirming with no row ticked is accepted
 */
public record MenuSpec(
    String title,
    List<String> labels,
    String emptySelectionMessage,
    Set<Integer> initialSelections,
    boolean allowEmpty) {

  /** Copies the collections so the menu does not observe later changes. */
  public MenuSpec {
    labels = List.copyOf(labels);
    initialSelections = Set.copyOf(initialSelections);
  }
}
