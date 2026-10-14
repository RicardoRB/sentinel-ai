package dev.sentinel.domain.spec;

/** How an agent tool call affects a path. */
public enum GuardOperation {
  WRITE,
  DELETE,
  MOVE_FROM,
  MOVE_TO;

  /** Whether the operation also removes everything beneath its target. */
  public boolean removesTree() {
    return this == DELETE || this == MOVE_FROM;
  }
}
