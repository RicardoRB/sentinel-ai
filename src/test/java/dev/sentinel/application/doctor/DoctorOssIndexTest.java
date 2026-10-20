package dev.sentinel.application.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.doctor.EnvironmentFacts;
import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.doctor.OssIndexSetting;
import dev.sentinel.infrastructure.config.TomlConfigurationReader;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DoctorOssIndexTest {
  private static final String FINDING = "dependency-check-oss-index";
  private static final String SECRET = "s3cret-token";

  @TempDir Path root;

  @BeforeEach
  void project() throws IOException {
    Files.writeString(root.resolve("pom.xml"), "<project></project>");
    Files.writeString(
        root.resolve("sentinel.toml"),
        "version = 1\n[quality-gates.dependency-check]\nenabled = true\n"
            + "command = \"./mvnw dependency-check:check\"\n");
  }

  @Test
  void warnsWhenEnabledWithoutPassword() {
    assertThat(names(diagnose(OssIndexSetting.ENABLED, false))).contains(FINDING);
  }

  @Test
  void staysQuietWhenGeneratedDefaultIsDisabled() {
    assertThat(names(diagnose(OssIndexSetting.DISABLED, false))).doesNotContain(FINDING);
  }

  @Test
  void staysQuietWhenPasswordIsSet() {
    assertThat(names(diagnose(OssIndexSetting.ENABLED, true))).doesNotContain(FINDING);
  }

  @Test
  void staysQuietWhenBuildFileIsUnreadable() {
    assertThat(names(diagnose(OssIndexSetting.UNREADABLE, false))).doesNotContain(FINDING);
  }

  @Test
  void neverPrintsCredentialValues() {
    assertThat(diagnose(OssIndexSetting.ENABLED, false))
        .extracting(DoctorService.Finding::message)
        .noneMatch(message -> message.contains(SECRET));
  }

  @Test
  void staysQuietWhenGateIsDisabled() throws IOException {
    Files.writeString(
        root.resolve("sentinel.toml"),
        "version = 1\n[quality-gates.dependency-check]\nenabled = false\n"
            + "command = \"./mvnw dependency-check:check\"\n");

    assertThat(names(diagnose(OssIndexSetting.ENABLED, false))).doesNotContain(FINDING);
  }

  private List<DoctorService.Finding> diagnose(
      final OssIndexSetting setting, final boolean passwordSet) {
    final EnvironmentInspection environment =
        new EnvironmentInspection() {
          @Override
          public EnvironmentFacts inspect(final Path projectRoot) {
            return new EnvironmentFacts(
                Set.of(),
                Map.of(
                    "configurationPresent",
                    "true",
                    "OSS_INDEX_PASSWORD",
                    Boolean.toString(passwordSet),
                    "OSS_INDEX_USERNAME_VALUE",
                    SECRET));
          }

          @Override
          public OssIndexSetting ossIndexSetting(final Path projectRoot) {
            return setting;
          }
        };
    return new DoctorService(
            new ProjectDetector(new FileSystemProjectInspection()),
            environment,
            new TomlConfigurationReader())
        .diagnose(root);
  }

  private static List<String> names(final List<DoctorService.Finding> findings) {
    return findings.stream().map(DoctorService.Finding::name).toList();
  }
}
