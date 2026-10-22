package dev.ward.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.agent.GuardMode;
import dev.ward.domain.agent.IntegrationRequest;
import dev.ward.domain.agent.IntegrationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Upgrades the artifacts Ward 0.0.7 generated, captured in {@code native-adapters-0.0.7}, to the
 * repaired adapters: refresh replaces owned files, keeps the installed guard state, is idempotent,
 * and never touches unrelated files.
 */
class NativeAdapterUpgradeTest {
  private static final Path CAPTURED = Path.of("src/test/resources/fixtures/native-adapters-0.0.7");
  private static final String USER_FILE = "notes-from-the-user.txt";

  static List<NativeHarnessAdapter> adapters() {
    return NativeAdapterSnapshotTest.adapters();
  }

  /** The 0.0.7 config path, hook path, config and enabled-guard hook of an adapter. */
  private record Captured(String configFile, String hookFile, String config, String hook) {
    static Captured of(final String id) throws IOException {
      final String text = Files.readString(CAPTURED.resolve(id + ".txt"));
      final String[] definition = section(text, "definition").split(" ");
      return new Captured(
          definition[1], definition[2], section(text, "config"), section(text, "hook ENABLE"));
    }

    private static String section(final String text, final String name) {
      final int start = text.indexOf("== " + name + "\n") + name.length() + 4;
      final int end = text.indexOf("\n== ", start);
      return text.substring(start, end);
    }
  }

  private static void write(final Path file, final String content) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  @ParameterizedTest
  @MethodSource("adapters")
  void refreshUpgradesTheCapturedArtifactsAndKeepsTheGuard(
      final NativeHarnessAdapter adapter, @TempDir final Path root) throws IOException {
    final Captured old = Captured.of(adapter.id());
    write(root.resolve(old.configFile()), old.config());
    write(root.resolve(old.hookFile()), old.hook());
    final Path userFile = root.resolve(adapter.definition().hookFile()).resolveSibling(USER_FILE);
    write(userFile, "mine");

    assertThat(adapter.integrate(root, IntegrationRequest.INSTALL).status())
        .isEqualTo(IntegrationResult.Status.CHANGED);

    final Path config = root.resolve(adapter.definition().configFile());
    final Path hook = root.resolve(adapter.definition().hookFile());
    assertThat(config).hasContent(adapter.configContent());
    // A refresh without a guard option keeps a guard the 0.0.7 wrapper had enabled.
    final boolean guarded = old.hook().contains("\"enable\" = \"enable\" ]");
    assertThat(hook)
        .hasContent(adapter.hookContent(guarded ? GuardMode.ENABLE : GuardMode.DISABLE));
    assertThat(adapter.integrate(root, IntegrationRequest.INSTALL).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
    assertThat(userFile).hasContent("mine");
  }

  @ParameterizedTest
  @MethodSource("adapters")
  void removalDeletesOnlyOwnedFiles(final NativeHarnessAdapter adapter, @TempDir final Path root)
      throws IOException {
    adapter.integrate(root, new IntegrationRequest(false, GuardMode.ENABLE));
    final Path userFile = root.resolve(adapter.definition().hookFile()).resolveSibling(USER_FILE);
    write(userFile, "mine");

    assertThat(adapter.integrate(root, IntegrationRequest.REMOVE).status())
        .isEqualTo(IntegrationResult.Status.REMOVED);

    assertThat(root.resolve(adapter.definition().configFile())).doesNotExist();
    assertThat(root.resolve(adapter.definition().hookFile())).doesNotExist();
    assertThat(userFile).hasContent("mine");
  }
}
