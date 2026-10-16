package dev.sentinel.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.agent.SupportedAgents;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Keeps the offline build honest: every registered harness needs a compatibility manifest entry.
 * Schema and version-pinning rules are validated by the opt-in suite under {@code compatibility/}.
 */
class CompatibilityManifestInventoryTest {
  private static final Path MANIFEST = Path.of("compatibility", "manifest.json");
  private static final Pattern HARNESS_ID = Pattern.compile("^\\s{6}\"id\": \"([^\"]+)\",$");

  @Test
  void everyRegisteredHarnessHasExactlyOneManifestEntry() throws IOException {
    assertThat(manifestIds(Files.readString(MANIFEST, StandardCharsets.UTF_8)))
        .containsExactlyElementsOf(SupportedAgents.ids());
  }

  private static List<String> manifestIds(final String manifest) {
    return manifest
        .lines()
        .map(HARNESS_ID::matcher)
        .filter(Matcher::matches)
        .map(matcher -> matcher.group(1))
        .toList();
  }
}
