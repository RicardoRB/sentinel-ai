package dev.sentinel.application.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.FakeEnvironmentInspection;
import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.GuardMode;
import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.agent.PlannedFileChange;
import dev.sentinel.domain.init.InitSelection;
import dev.sentinel.infrastructure.init.ArchitectureTestGenerator;
import dev.sentinel.infrastructure.init.FileConfigurationStorage;
import dev.sentinel.infrastructure.init.PomToolConfigurator;
import dev.sentinel.infrastructure.init.PresetRuleFileWriter;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitPomValidationRollbackTest {
  private static final String HOOK = ".opencode/hook";

  @Test
  void rollsBackInstalledIntegrationWhenPomValidationFails(@TempDir final Path root)
      throws IOException {
    final String original = "<project>\n  <name>&undefined;</name>\n  <build/>\n</project>\n";
    Files.writeString(root.resolve("pom.xml"), original);
    final FileWritingIntegration integration = new FileWritingIntegration();
    final InitService service =
        new InitService(
            new ProjectDetector(new FileSystemProjectInspection()),
            Set.of(integration),
            new InitSetupCatalog(new FakeEnvironmentInspection()),
            new InitProjectFiles(
                new PomToolConfigurator(),
                new PresetRuleFileWriter(),
                new ArchitectureTestGenerator(),
                new FileConfigurationStorage()));

    assertThatThrownBy(
            () -> service.initialize(root, new InitSelection(List.of("opencode"), List.of("pmd"))))
        .hasMessageContaining("Cannot safely update pom.xml while adding maven-pmd-plugin");
    assertThat(root.resolve("sentinel.toml")).doesNotExist();
    assertThat(integration.installs).isEqualTo(1);
    assertThat(root.resolve(HOOK)).doesNotExist();
    assertThat(Files.readString(root.resolve("pom.xml"))).isEqualTo(original);
  }

  /** Writes a file on install and deletes it on removal, like a real integration. */
  private static final class FileWritingIntegration implements AgentIntegration {
    private int installs;

    @Override
    public List<PlannedFileChange> plan(final Path projectRoot, final GuardMode guard) {
      return List.of();
    }

    @Override
    public String id() {
      return "opencode";
    }

    @Override
    public IntegrationResult integrate(final Path root, final boolean remove) {
      final Path hook = root.resolve(HOOK);
      try {
        if (remove) {
          Files.deleteIfExists(hook);
          return new IntegrationResult(IntegrationResult.Status.REMOVED, List.of(HOOK), "removed");
        }
        installs++;
        Files.createDirectories(hook.getParent());
        Files.writeString(hook, "hook");
        return new IntegrationResult(IntegrationResult.Status.CHANGED, List.of(HOOK), "installed");
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
