package dev.ward.domain.init;

import java.util.List;
import java.util.stream.Stream;

/** How a preset's gate set combines with gates the user chose or gave explicitly. */
public final class PresetGateOrder {
  private PresetGateOrder() {}

  /**
   * Returns {@code selected} with the selected preset gates first, in preset order, followed by the
   * other selected gates in their given order. Unselected preset gates are not added.
   */
  public static List<String> orderSelected(
      final List<String> presetGates, final List<String> selected) {
    return Stream.concat(presetGates.stream().filter(selected::contains), selected.stream())
        .distinct()
        .toList();
  }

  /**
   * Returns {@code presetGates} followed by every explicit gate not already present, in the given
   * order. Explicit gates never remove preset gates.
   */
  public static List<String> withExplicitGates(
      final List<String> presetGates, final List<String> explicit) {
    return Stream.concat(presetGates.stream(), explicit.stream()).distinct().toList();
  }
}
