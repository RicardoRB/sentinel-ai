package dev.ward.infrastructure.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.doctor.OssIndexSetting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DependencyCheckSettingsTest {
  private static final String MAVEN_PLUGIN =
      "<project><build><plugins><plugin><artifactId>dependency-check-maven</artifactId>"
          + "<configuration>%s</configuration></plugin></plugins></build></project>";

  private static final byte[] INVALID_UTF8 = {(byte) 0xC3, (byte) 0x28};

  @TempDir Path root;

  @Test
  void mavenGeneratedConfigurationIsDisabled() throws IOException {
    Files.writeString(
        root.resolve("pom.xml"),
        MAVEN_PLUGIN.formatted("<ossindexAnalyzerEnabled>false</ossindexAnalyzerEnabled>"));

    assertThat(DependencyCheckSettings.read(root)).isEqualTo(OssIndexSetting.DISABLED);
  }

  @Test
  void mavenPluginWithoutSettingUsesTheEnabledDefault() throws IOException {
    Files.writeString(root.resolve("pom.xml"), MAVEN_PLUGIN.formatted(""));

    assertThat(DependencyCheckSettings.read(root)).isEqualTo(OssIndexSetting.ENABLED);
  }

  @Test
  void gradleOssIndexBlockIsDisabled() throws IOException {
    Files.writeString(
        root.resolve("build.gradle.kts"),
        "plugins { id(\"org.owasp.dependencycheck\") version \"12.1.0\" }\n"
            + "dependencyCheck { analyzers { ossIndex { enabled = false } } }\n");

    assertThat(DependencyCheckSettings.read(root)).isEqualTo(OssIndexSetting.DISABLED);
  }

  @Test
  void gradleExplicitlyEnabledIsEnabled() throws IOException {
    Files.writeString(
        root.resolve("build.gradle"),
        "plugins { id 'org.owasp.dependencycheck' version '12.1.0' }\n"
            + "dependencyCheck { analyzers { ossIndex { enabled = true } } }\n");

    assertThat(DependencyCheckSettings.read(root)).isEqualTo(OssIndexSetting.ENABLED);
  }

  @Test
  void buildWithoutDependencyCheckIsAbsent() throws IOException {
    Files.writeString(root.resolve("pom.xml"), "<project></project>");

    assertThat(DependencyCheckSettings.read(root)).isEqualTo(OssIndexSetting.ABSENT);
  }

  @Test
  void unreadableBuildFileIsUnreadable() throws IOException {
    Files.write(root.resolve("pom.xml"), INVALID_UTF8);

    assertThat(DependencyCheckSettings.read(root)).isEqualTo(OssIndexSetting.UNREADABLE);
  }
}
