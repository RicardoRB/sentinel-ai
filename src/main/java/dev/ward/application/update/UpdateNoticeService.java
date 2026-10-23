package dev.ward.application.update;

import dev.ward.domain.update.CachedCheck;
import dev.ward.domain.update.LatestReleaseLookup;
import dev.ward.domain.update.ReleaseVersion;
import dev.ward.domain.update.UpdateCheckCache;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.inject.Named;

/** Decides whether a newer Ward release exists and how to tell the user about it. */
public final class UpdateNoticeService {
  /** Environment variable that turns every update lookup off. */
  public static final String OPT_OUT = "WARD_NO_UPDATE_CHECK";

  private static final Logger LOGGER = Logger.getLogger(UpdateNoticeService.class.getName());
  private static final Duration THROTTLE = Duration.ofHours(24);
  private static final String CI = "CI";
  private static final String INSTALL =
      "Update: curl -fsSL https://raw.githubusercontent.com/RicardoRB/ward-ai/main/install.sh"
          + " | sh";
  private static final String INSTALL_WINDOWS =
      "Update: irm https://raw.githubusercontent.com/RicardoRB/ward-ai/main/install.ps1 | iex";

  private final String runningVersion;
  private final LatestReleaseLookup lookup;
  private final Supplier<UpdateCheckCache> cache;
  private final UpdateContext context;

  /** Creates the service for the running process. */
  @Inject
  public UpdateNoticeService(
      @Named("ward.version") final String runningVersion,
      final LatestReleaseLookup lookup,
      final UpdateCheckCache cache) {
    this(runningVersion, lookup, cache, UpdateContext.system());
  }

  /** Creates the service with explicit host facts. */
  public UpdateNoticeService(
      final String runningVersion,
      final LatestReleaseLookup lookup,
      final UpdateCheckCache cache,
      final UpdateContext context) {
    this.runningVersion = runningVersion;
    this.lookup = lookup;
    this.cache = () -> cache;
    this.context = context;
  }

  /**
   * Returns the notice line to print after an interactive command, or empty when none is due. At
   * most one lookup runs per day; other runs reuse the cached result.
   */
  public Optional<String> automaticNotice() {
    final Optional<ReleaseVersion> running = ReleaseVersion.parse(runningVersion);
    final String skip = isSet(CI) ? "ci" : isSet(OPT_OUT) ? "opt-out" : null;
    if (skip != null || running.isEmpty()) {
      LOGGER.log(
          Level.FINE,
          () ->
              "event=update-check status=skipped reason=" + (skip == null ? "development" : skip));
      return Optional.empty();
    }
    final Instant now = context.clock().instant();
    final Optional<CachedCheck> cached =
        cache.get().read().filter(check -> now.isBefore(check.checkedAt().plus(THROTTLE)));
    final ReleaseVersion latest;
    if (cached.isPresent()) {
      LOGGER.log(Level.FINE, "event=update-check status=cached");
      latest = cached.get().latest();
    } else {
      final Optional<ReleaseVersion> found = lookup.latest();
      LOGGER.log(
          Level.FINE,
          () ->
              "event=update-check status=looked-up result="
                  + (found.isPresent() ? "found" : "unknown"));
      // An unknown result is cached as current so an offline machine waits a day before retrying.
      latest = found.orElse(running.get());
      cache.get().write(new CachedCheck(now, latest));
    }
    return latest.isNewerThan(running.get())
        ? Optional.of("ward: " + outdated(latest, running.get()))
        : Optional.empty();
  }

  /** Looks up the latest release now, ignoring the cache, for an explicit diagnostic. */
  public UpdateStatus freshStatus() {
    if (isSet(OPT_OUT)) {
      return new UpdateStatus(
          UpdateStatus.State.SKIPPED, "Update check disabled by " + OPT_OUT + ".");
    }
    final Optional<ReleaseVersion> running = ReleaseVersion.parse(runningVersion);
    if (running.isEmpty()) {
      return new UpdateStatus(
          UpdateStatus.State.SKIPPED, "Development build " + runningVersion + ".");
    }
    final Optional<ReleaseVersion> latest = lookup.latest();
    if (latest.isEmpty()) {
      return new UpdateStatus(
          UpdateStatus.State.UNKNOWN,
          "Could not determine the latest release (you have v" + running.get() + ").");
    }
    return latest.get().isNewerThan(running.get())
        ? new UpdateStatus(UpdateStatus.State.OUTDATED, outdated(latest.get(), running.get()))
        : new UpdateStatus(
            UpdateStatus.State.CURRENT, "v" + running.get() + " is the latest release.");
  }

  private String outdated(final ReleaseVersion latest, final ReleaseVersion running) {
    return "v"
        + latest
        + " is available (you have v"
        + running
        + "). "
        + (context.windows() ? INSTALL_WINDOWS : INSTALL);
  }

  private boolean isSet(final String name) {
    final String value = context.environment().apply(name);
    return value != null && !value.isEmpty();
  }
}
