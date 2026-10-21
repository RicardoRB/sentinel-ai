package dev.sentinel.domain.update;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A released Sentinel version, {@code MAJOR.MINOR.PATCH}, ordered numerically.
 *
 * @param major the major number
 * @param minor the minor number
 * @param patch the patch number
 */
public record ReleaseVersion(int major, int minor, int patch) {
  private static final Pattern RELEASE = Pattern.compile("v?(\\d{1,9})\\.(\\d{1,9})\\.(\\d{1,9})");
  private static final int PATCH_GROUP = 3;
  private static final Comparator<ReleaseVersion> ORDER =
      Comparator.comparingInt(ReleaseVersion::major)
          .thenComparingInt(ReleaseVersion::minor)
          .thenComparingInt(ReleaseVersion::patch);

  /**
   * Parses {@code text} such as {@code 1.4.0} or {@code v1.4.0}; returns empty for development
   * builds and any other shape.
   */
  public static Optional<ReleaseVersion> parse(final String text) {
    if (text == null) {
      return Optional.empty();
    }
    final Matcher matcher = RELEASE.matcher(text.strip());
    if (!matcher.matches()) {
      return Optional.empty();
    }
    return Optional.of(
        new ReleaseVersion(
            Integer.parseInt(matcher.group(1)),
            Integer.parseInt(matcher.group(2)),
            Integer.parseInt(matcher.group(PATCH_GROUP))));
  }

  /** Returns whether this version is strictly newer than {@code other}. */
  public boolean isNewerThan(final ReleaseVersion other) {
    return ORDER.compare(this, other) > 0;
  }

  @Override
  public String toString() {
    return major + "." + minor + "." + patch;
  }
}
