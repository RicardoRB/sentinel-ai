package dev.sentinel;

import java.util.Objects;

/** Shared scalar validation and value semantics for the JSON assertion tree. */
class JsonTreeValue {
  protected final Object value;

  JsonTreeValue(final Object value) {
    this.value = value;
  }

  public Object value() {
    return value;
  }

  protected final <T> T as(final Class<T> type) {
    if (!type.isInstance(value)) {
      throw new AssertionError(
          "Expected JSON " + type.getSimpleName() + " but was " + Objects.toString(value));
    }
    return type.cast(value);
  }

  @Override
  public boolean equals(final Object other) {
    return other instanceof JsonTreeValue tree && Objects.equals(value, tree.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }

  @Override
  public String toString() {
    return Objects.toString(value);
  }
}
