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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;

/** The PMD plugin init declares reuses its analysis cache, so repeated checks stay fast. */
class PomPmdAnalysisCacheTest {
  private static final String ANALYSIS_CACHE = "<analysisCache>true</analysisCache>";

  @TempDir Path root;

  @ParameterizedTest
  @NullSource
  @EnumSource(QualityPreset.class)
  void addedPmdPluginEnablesTheAnalysisCache(final QualityPreset preset) throws IOException {
    final Path pom = root.resolve("pom.xml");
    Files.writeString(pom, "<project>\n  <build/>\n</project>\n");

    new PomToolConfigurator().apply(project(), List.of(gate()), preset, ignored -> true);

    assertThat(Files.readString(pom))
        .contains("<artifactId>maven-pmd-plugin</artifactId>")
        .containsOnlyOnce(ANALYSIS_CACHE);
  }

  @ParameterizedTest
  @NullSource
  @EnumSource(QualityPreset.class)
  void existingPmdDeclarationIsLeftUntouched(final QualityPreset preset) throws IOException {
    final Path pom = root.resolve("pom.xml");
    final String existing =
        """
        <project>
          <build>
            <plugins>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-pmd-plugin</artifactId>
                <version>3.28.0</version>
              </plugin>
            </plugins>
          </build>
        </project>
        """;
    Files.writeString(pom, existing);

    new PomToolConfigurator().apply(project(), List.of(gate()), preset, ignored -> true);

    assertThat(Files.readString(pom)).doesNotContain(ANALYSIS_CACHE);
  }

  private Project project() {
    return new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  }

  private static InitGateOption gate() {
    return new InitGateOption("pmd", "pmd", List.of("mvn", "pmd:check"), true, "available");
  }
}
