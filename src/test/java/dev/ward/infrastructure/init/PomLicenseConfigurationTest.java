package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The plugin matches license names exactly, so common spellings merge into the denylist or, under
 * the strict preset, the allowlist.
 */
class PomLicenseConfigurationTest {
  private static final String POM =
      "<project><modelVersion>4.0.0</modelVersion><groupId>com.acme</groupId>"
          + "<artifactId>app</artifactId><version>1</version></project>";

  @TempDir Path root;

  @Test
  void mergesCommonGplAndAgplSpellingsIntoTheExcludedLicenses() throws IOException {
    final String pom = configure(QualityPreset.STANDARD);

    assertThat(pom)
        .contains(
            "<licenseMerge>GNU General Public License (GPL)|GPL-3.0</licenseMerge>",
            "<licenseMerge>GNU General Public License (GPL)|GNU General Public License v2.0"
                + "</licenseMerge>",
            "<licenseMerge>GNU Affero General Public License (AGPL)|AGPL-3.0</licenseMerge>",
            "<excludedLicense>GNU General Public License (GPL)</excludedLicense>",
            "<excludedLicense>GNU Affero General Public License (AGPL)</excludedLicense>");
  }

  @Test
  void leavesClasspathExceptionLicensesAllowed() throws IOException {
    assertThat(configure(QualityPreset.STANDARD)).doesNotContainIgnoringCase("classpath exception");
  }

  @Test
  void strictPresetAllowsOnlyListedLicenses() throws IOException {
    final String pom = configure(QualityPreset.STRICT);

    assertThat(pom)
        .contains(
            "<includedLicense>Apache-2.0</includedLicense>",
            "<includedLicense>EPL-2.0</includedLicense>",
            "<licenseMerge>Apache-2.0|The Apache Software License, Version 2.0</licenseMerge>",
            "<licenseMerge>MIT|The MIT License</licenseMerge>")
        .doesNotContain("<excludedLicenses>", "<includedLicense>LGPL");
  }

  @Test
  void configurationWithoutPresetKeepsTheDenylist() throws IOException {
    assertThat(configure(null)).contains("<excludedLicenses>").doesNotContain("<includedLicenses>");
  }

  private String configure(final QualityPreset preset) throws IOException {
    Files.writeString(root.resolve("pom.xml"), POM);
    new PomToolConfigurator()
        .apply(
            new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE),
            List.of(
                new InitGateOption(
                    "license", "license", List.of("mvn", "license:add-third-party"), true, "ok")),
            preset);
    return Files.readString(root.resolve("pom.xml"));
  }
}
