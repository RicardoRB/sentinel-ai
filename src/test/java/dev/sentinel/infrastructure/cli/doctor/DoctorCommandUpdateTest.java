package dev.sentinel.infrastructure.cli.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.application.doctor.DoctorService;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.application.update.UpdateContext;
import dev.sentinel.application.update.UpdateNoticeService;
import dev.sentinel.domain.doctor.EnvironmentFacts;
import dev.sentinel.domain.update.CachedCheck;
import dev.sentinel.domain.update.ReleaseVersion;
import dev.sentinel.domain.update.UpdateCheckCache;
import dev.sentinel.infrastructure.cli.ExitCodes;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class DoctorCommandUpdateTest {
  private static final String TRUE = "true";
  private static final ReleaseVersion NEWER = new ReleaseVersion(0, 4, 0);
  private static final ReleaseVersion CURRENT = new ReleaseVersion(0, 3, 1);
  private static final UpdateCheckCache NO_CACHE =
      new UpdateCheckCache() {
        @Override
        public Optional<CachedCheck> read() {
          return Optional.empty();
        }

        @Override
        public void write(final CachedCheck check) {
          // Doctor never caches.
        }
      };

  @TempDir Path directory;

  private final StringWriter out = new StringWriter();

  private int doctor(
      final Optional<ReleaseVersion> latest, final Map<String, String> env, final Path start) {
    final DoctorService diagnostics =
        new DoctorService(
            new ProjectDetector(new FileSystemProjectInspection()),
            root ->
                new EnvironmentFacts(
                    Set.of(), Map.of("mavenWrapperAvailable", TRUE, "configurationPresent", TRUE)));
    final UpdateNoticeService updates =
        new UpdateNoticeService(
            "0.3.1",
            () -> latest,
            NO_CACHE,
            new UpdateContext(InstantSource.system(), env::get, false));
    final CommandLine cli = new CommandLine(new DoctorCommand(diagnostics, updates));
    cli.setOut(new PrintWriter(out, true));
    return cli.execute("-C", start.toString());
  }

  private Path project() throws IOException {
    Files.writeString(directory.resolve("pom.xml"), "<project/>");
    return directory;
  }

  @Test
  void outdatedBinaryWarnsWithoutFailingDoctor() throws IOException {
    assertThat(doctor(Optional.of(NEWER), Map.of(), project())).isZero();

    assertThat(out.toString())
        .contains("[WARNING] sentinel      v0.4.0 is available (you have v0.3.1). Update:");
  }

  @Test
  void currentBinaryIsOk() throws IOException {
    assertThat(doctor(Optional.of(CURRENT), Map.of(), project())).isZero();

    assertThat(out.toString()).contains("[OK] sentinel      v0.3.1 is the latest release.");
  }

  @Test
  void versionIsReportedOutsideAProject() {
    assertThat(doctor(Optional.of(NEWER), Map.of(), directory)).isEqualTo(ExitCodes.ERROR);

    assertThat(out.toString()).contains("No supported project detected.", "[WARNING] sentinel");
  }

  @Test
  void failedLookupWarns() throws IOException {
    assertThat(doctor(Optional.empty(), Map.of(), project())).isZero();

    assertThat(out.toString())
        .contains("[WARNING] sentinel      Could not determine the latest release");
  }

  @Test
  void optOutSkipsTheLookup() throws IOException {
    assertThat(doctor(Optional.of(NEWER), Map.of(UpdateNoticeService.OPT_OUT, "1"), project()))
        .isZero();

    assertThat(out.toString())
        .contains("[OK] sentinel      Update check disabled by SENTINEL_NO_UPDATE_CHECK.");
  }
}
