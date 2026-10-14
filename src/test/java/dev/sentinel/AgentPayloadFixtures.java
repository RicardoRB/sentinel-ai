package dev.sentinel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Recorded agent tool-call payloads under {@code src/test/resources/fixtures/agent-payloads}. */
public final class AgentPayloadFixtures {
  private static final Path ROOT = Path.of("src/test/resources/fixtures/agent-payloads");

  private AgentPayloadFixtures() {}

  /** Returns the raw text of a fixture, such as {@code claude-code/pre-tool-write.json}. */
  public static String text(final String fixture) {
    try {
      return Files.readString(ROOT.resolve(fixture), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Returns a fixture parsed as JSON. */
  public static JsonTree json(final String fixture) {
    return JsonTree.parse(text(fixture));
  }
}
