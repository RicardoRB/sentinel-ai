package dev.sentinel.application.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.ProjectFixtures;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.FakeEnvironmentInspection;
import dev.sentinel.domain.init.InitSelection;
import dev.sentinel.domain.init.ToolInstallationChange;
import dev.sentinel.domain.init.ToolInstallationResult;
import dev.sentinel.domain.init.ToolInstaller;
import dev.sentinel.infrastructure.init.ArchitectureTestGenerator;
import dev.sentinel.infrastructure.init.FileConfigurationStorage;
import dev.sentinel.infrastructure.init.PomToolConfigurator;
import dev.sentinel.infrastructure.init.PresetRuleFileWriter;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitToolLifecycleTest {
  @Test
  void selectedToolWithoutConsentDoesNotInvokeInstaller(@TempDir final Path root) throws Exception {
    ProjectFixtures.withPom(root, ProjectFixtures.PLAIN_POM);
    final RecordingInstaller installer = new RecordingInstaller(true);

    service(installer).initialize(root, selection(List.of(), List.of()));

    assertThat(installer.installCalled).isFalse();
    assertThat(Files.readString(root.resolve("sentinel.toml"))).doesNotContain("openspec");
  }

  @Test
  void approvedInstallUsesInstallerAndWritesRelativeExecutable(@TempDir final Path root)
      throws Exception {
    ProjectFixtures.withPom(root, ProjectFixtures.PLAIN_POM);
    final RecordingInstaller installer = new RecordingInstaller(true);

    service(installer).initialize(root, selection(List.of("openspec"), List.of("openspec")));

    assertThat(installer.installCalled).isTrue();
    assertThat(Files.readString(root.resolve("sentinel.toml")))
        .contains(".sentinel/tools/openspec/openspec");
  }

  @Test
  void gateOnlyConsentInstallsTheGateToolAndNotOpenSpec(@TempDir final Path root) throws Exception {
    ProjectFixtures.withPom(root, ProjectFixtures.PLAIN_POM);
    final RecordingInstaller installer = new RecordingInstaller(true);

    service(installer)
        .initialize(
            root,
            new InitSelection(
                List.of("none"),
                List.of("tests", "semgrep"),
                null,
                null,
                List.of(),
                List.of(),
                List.of("semgrep"),
                false));

    assertThat(installer.installed).containsExactly("semgrep");
    assertThat(Files.readString(root.resolve("sentinel.toml"))).doesNotContain("openspec");
  }

  @Test
  void unavailableInstallerLeavesProjectUnchanged(@TempDir final Path root) throws Exception {
    ProjectFixtures.withPom(root, ProjectFixtures.PLAIN_POM);
    final RecordingInstaller installer = new RecordingInstaller(false);

    assertThatThrownBy(
            () ->
                service(installer)
                    .initialize(root, selection(List.of("openspec"), List.of("openspec"))))
        .hasMessageContaining("installation failed");
    assertThat(root.resolve("sentinel.toml")).doesNotExist();
  }

  private static InitSelection selection(
      final List<String> installTools, final List<String> validations) {
    return new InitSelection(
        List.of("none"),
        List.of("tests"),
        null,
        null,
        List.of("openspec"),
        validations,
        installTools,
        false);
  }

  private static InitService service(final ToolInstaller installer) {
    return new InitService(
        new ProjectDetector(new FileSystemProjectInspection()),
        Set.of(),
        new InitSetupCatalog(new FakeEnvironmentInspection()),
        new InitProjectFiles(
            new PomToolConfigurator(),
            new PresetRuleFileWriter(),
            new ArchitectureTestGenerator(),
            new FileConfigurationStorage()),
        installer);
  }

  private static final class RecordingInstaller implements ToolInstaller {
    private final boolean succeeds;
    private final List<String> installed = new ArrayList<>();
    private boolean installCalled;

    private RecordingInstaller(final boolean succeeds) {
      this.succeeds = succeeds;
    }

    @Override
    public ToolInstallationResult install(final Path projectRoot, final String tool) {
      installCalled = true;
      installed.add(tool);
      if (!succeeds) {
        return new ToolInstallationResult(tool, false, null, "fake installer unavailable");
      }
      return new ToolInstallationResult(
          tool,
          true,
          Path.of(".sentinel/tools/openspec/openspec"),
          "installed",
          new ToolInstallationChange(null, projectRoot.resolve(".sentinel/tools/openspec"), true));
    }
  }
}
