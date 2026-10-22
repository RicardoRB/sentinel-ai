package dev.ward.application.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.doctor.ExecutableObservation;
import dev.ward.domain.doctor.OpenSpecIgnoreWarning;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DoctorOpenSpecIgnoreTest {
  @TempDir Path root;

  @BeforeEach
  void project() throws IOException {
    Files.writeString(root.resolve("pom.xml"), "<project></project>");
    Files.createDirectories(root.resolve("openspec"));
    configure(true);
  }

  @Test
  void warnsWhenOpenSpecDirectoryIsIgnored() {
    assertThat(diagnose(true))
        .contains(
            new DoctorService.Finding(
                "openspec", DoctorService.Status.WARNING, OpenSpecIgnoreWarning.MESSAGE));
  }

  @Test
  void staysQuietWhenDirectoryIsTracked() {
    assertThat(diagnose(false))
        .extracting(DoctorService.Finding::message)
        .doesNotContain(OpenSpecIgnoreWarning.MESSAGE);
  }

  @Test
  void staysQuietWhenGateIsDisabled() throws IOException {
    configure(false);

    assertThat(diagnose(true))
        .extracting(DoctorService.Finding::message)
        .doesNotContain(OpenSpecIgnoreWarning.MESSAGE);
  }

  private void configure(final boolean enabled) throws IOException {
    Files.writeString(
        root.resolve("ward.toml"),
        "version = 1\n[quality-gates.openspec]\nenabled = "
            + enabled
            + "\ncommand = \"openspec validate --all\"\n");
  }

  private List<DoctorService.Finding> diagnose(final boolean ignored) {
    final EnvironmentInspection environment =
        new EnvironmentInspection() {
          @Override
          public EnvironmentFacts inspect(final Path projectRoot) {
            return new EnvironmentFacts(Set.of(), Map.of("configurationPresent", "true"));
          }

          @Override
          public ExecutableObservation inspectExecutable(
              final Path projectRoot, final String executable) {
            return new ExecutableObservation(executable, Path.of(executable), true, true, true);
          }

          @Override
          public boolean hasOpenSpecDirectory(final Path projectRoot) {
            return true;
          }

          @Override
          public boolean isOpenSpecDirectoryIgnored(final Path projectRoot) {
            return ignored;
          }
        };
    return new DoctorService(
            new ProjectDetector(new FileSystemProjectInspection()),
            environment,
            new TomlConfigurationReader())
        .diagnose(root);
  }
}
