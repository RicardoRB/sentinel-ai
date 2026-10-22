package dev.ward.domain.agent;

/** What an integration run does with the opt-in approved-artifact guard. */
public enum GuardMode {
  /** Keep the guard installed if it already is, and absent otherwise. */
  KEEP,
  /** Install or refresh the guard. */
  ENABLE,
  /** Remove the guard and leave the rest of the integration installed. */
  DISABLE;

  /** Maps a negatable command-line option: absent keeps, present enables, negated disables. */
  public static GuardMode of(final Boolean option) {
    if (option == null) {
      return KEEP;
    }
    return option ? ENABLE : DISABLE;
  }
}
