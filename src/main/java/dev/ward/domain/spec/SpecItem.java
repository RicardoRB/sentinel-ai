package dev.ward.domain.spec;

import java.util.Locale;
import java.util.Objects;

/** One validatable specification item, such as an OpenSpec change or main spec. */
public record SpecItem(Kind kind, String id) {

  /** Whether the item is an OpenSpec change or a main spec. */
  public enum Kind {
    CHANGE,
    SPEC;

    /** The item type name used by spec tooling, such as {@code change} or {@code spec}. */
    public String type() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  /**
   * Validates the item.
   *
   * @throws NullPointerException if {@code kind} is missing
   * @throws IllegalArgumentException if {@code id} is blank
   */
  public SpecItem {
    Objects.requireNonNull(kind, "kind");
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("Spec item id must not be blank");
    }
  }
}
