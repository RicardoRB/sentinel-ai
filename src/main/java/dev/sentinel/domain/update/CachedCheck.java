package dev.sentinel.domain.update;

import java.time.Instant;

/**
 * The outcome of the last automatic update lookup.
 *
 * @param checkedAt when the lookup ran
 * @param latest the latest release it found
 */
public record CachedCheck(Instant checkedAt, ReleaseVersion latest) {}
