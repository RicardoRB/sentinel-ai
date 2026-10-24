package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeOpenSpecEnvironment;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Init points nested OpenSpec gates at a repository-level openspec/ only when it must. */
class InitOpenSpecRootTest {
  private static final String OPENSPEC = "openspec";
  private static final int OPENSPEC_GATES = 2;

  @TempDir Path repository;

  private final OpenSpecDirectories directories =
      new OpenSpecDirectories(new FakeOpenSpecEnvironment(true));
  private Project api;

  @BeforeEach
  void nestedProject() throws IOException {
    final Path root = Files.createDirectories(repository.resolve("apps/api"));
    api =
        new Project(
            root,
            Language.JAVA,
            BuildTool.MAVEN,
            Framework.NONE,
            false,
            false,
            repository,
            Set.of());
  }

  @Test
  void repositoryLevelOpenSpecIsRecordedOnBothGates() throws IOException {
    Files.createDirectories(repository.resolve(OPENSPEC));

    final String root = directories.root(api);
    final String toml =
        ConfigurationTemplate.render(
            List.of(gate(OPENSPEC), gate("openspec-archived"), gate("tests")), null, api, root);

    assertThat(root).isEqualTo("../..");
    assertThat(toml.split("root = \"\\.\\./\\.\\.\"", -1)).hasSize(OPENSPEC_GATES + 1);
  }

  @Test
  void projectLevelOpenSpecLeavesTheRootUnset() throws IOException {
    Files.createDirectories(repository.resolve(OPENSPEC));
    Files.createDirectories(api.root().resolve(OPENSPEC));

    assertThat(directories.root(api)).isNull();
  }

  @Test
  void rootProjectAndMissingOpenSpecLeaveTheRootUnset() {
    assertThat(directories.root(api)).isNull();
    assertThat(
            directories.root(
                new Project(repository, Language.JAVA, BuildTool.MAVEN, Framework.NONE)))
        .isNull();
  }

  private static InitGateOption gate(final String id) {
    return new InitGateOption(id, id, List.of(id, "validate"), true, "");
  }
}
