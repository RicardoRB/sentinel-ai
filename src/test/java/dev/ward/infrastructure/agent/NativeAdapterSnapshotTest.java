package dev.ward.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.agent.GuardMode;
import dev.ward.domain.agent.NativeHookOutcome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Pins every native adapter's generated files and responses across refactoring. */
class NativeAdapterSnapshotTest {
  private static final Path SNAPSHOTS = Path.of("src/test/resources/fixtures/native-adapters");

  static List<NativeHarnessAdapter> adapters() {
    return List.of(
        new AmpIntegration(),
        new ClineIntegration(),
        new CodexIntegration(),
        new CopilotIntegration(),
        new CursorIntegration(),
        new FactoryDroidIntegration(),
        new GeminiIntegration(),
        new WindsurfIntegration());
  }

  static String snapshot(final NativeHarnessAdapter adapter) {
    final StringBuilder text = new StringBuilder(4096);
    text.append("== definition\n")
        .append(adapter.id())
        .append(' ')
        .append(adapter.definition().configFile())
        .append(' ')
        .append(adapter.definition().hookFile())
        .append(' ')
        .append(adapter.definition().events())
        .append(' ')
        .append(adapter.capabilities().stream().map(Enum::name).sorted().toList())
        .append("\n== config\n")
        .append(adapter.configContent());
    for (final GuardMode mode : GuardMode.values()) {
      text.append("\n== hook ").append(mode).append('\n').append(adapter.hookContent(mode));
    }
    text.append("\n== deny\n")
        .append(adapter.serialize(NativeHookOutcome.deny("Blocked \"quoted\" \\ path")))
        .append("\n== feedback\n")
        .append(adapter.serialize(NativeHookOutcome.feedback("Fix it")))
        .append("\n== error\n")
        .append(adapter.serialize(NativeHookOutcome.error("Broken")))
        .append("\n== allow\n")
        .append(adapter.serialize(NativeHookOutcome.allow()))
        .append('\n');
    return text.toString();
  }

  @ParameterizedTest
  @MethodSource("adapters")
  void generatedArtifactsAndResponsesMatchSnapshot(final NativeHarnessAdapter adapter)
      throws IOException {
    assertThat(snapshot(adapter))
        .isEqualTo(Files.readString(SNAPSHOTS.resolve(adapter.id() + ".txt")));
  }
}
