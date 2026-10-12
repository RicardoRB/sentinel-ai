package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.OpenSpecGuidanceLayout;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Bundled Sentinel-owned OpenSpec workflow guidance shared by every supported agent. */
public final class OpenSpecGuidance {
  public static final String MARKER = OpenSpecGuidanceLayout.OWNERSHIP_MARKER;
  public static final String SKILL_NAME = OpenSpecGuidanceLayout.SKILL_NAME;
  static final String RESOURCE = "/dev/sentinel/agent/" + SKILL_NAME + "/SKILL.md";

  private OpenSpecGuidance() {}

  /** Returns the guidance file content, which always contains {@link #MARKER}. */
  public static String content() {
    try (InputStream input = OpenSpecGuidance.class.getResourceAsStream(RESOURCE)) {
      if (input == null) {
        throw new IllegalStateException("Bundled OpenSpec guidance resource is missing.");
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read bundled OpenSpec guidance.", e);
    }
  }
}
