package dev.ward.application.update;

import java.time.InstantSource;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * The host facts an update check depends on.
 *
 * @param clock the current time
 * @param environment reads an environment variable, returning {@code null} when it is unset
 * @param windows whether the notice should show {@code install.ps1} instead of {@code install.sh}
 */
public record UpdateContext(
    InstantSource clock, UnaryOperator<String> environment, boolean windows) {
  /** Returns the context of the running process. */
  public static UpdateContext system() {
    return new UpdateContext(
        InstantSource.system(),
        System::getenv,
        System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows"));
  }
}
