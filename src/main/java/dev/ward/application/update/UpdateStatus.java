package dev.ward.application.update;

/**
 * The result of an explicit update lookup.
 *
 * @param state what the lookup found
 * @param message a one-line explanation, including install guidance when outdated
 */
public record UpdateStatus(State state, String message) {
  /** What an explicit update lookup found. */
  public enum State {
    /** The running version is the latest release. */
    CURRENT,
    /** A newer release exists. */
    OUTDATED,
    /** The latest release could not be determined. */
    UNKNOWN,
    /** The lookup was skipped by the opt-out or because this is a development build. */
    SKIPPED
  }
}
