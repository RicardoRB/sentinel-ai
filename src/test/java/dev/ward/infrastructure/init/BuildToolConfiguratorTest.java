package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuildToolConfiguratorTest {
  private static final Path POM = Path.of("app/pom.xml");

  private final RecordingAdapter jvm = new RecordingAdapter();
  private final BuildToolConfigurator router =
      new BuildToolConfigurator(Map.of(Toolchain.JVM, jvm));

  @Test
  void jvmProjectIsRoutedToTheJvmAdapter() {
    final BuildFileChange change = router.apply(project(BuildTool.GRADLE), List.of());

    assertThat(change.file()).isEqualTo(POM);
    assertThat(jvm.calls).containsExactly("apply");
  }

  @Test
  void unsupportedToolchainFailsNamingTheBuildTool() {
    assertThatThrownBy(() -> router.apply(project(BuildTool.CARGO), List.of()))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("Cargo")
        .hasMessageContaining("Maven and Gradle");
    assertThat(jvm.calls).isEmpty();
  }

  @Test
  void rollbackIsRoutedByTheChangeToolchain() {
    router.rollback(new BuildFileChange(Toolchain.JVM, POM, "", List.of()));

    assertThat(jvm.calls).containsExactly("rollback");
  }

  @Test
  void rollbackWithoutAnAdapterFails() {
    final BuildFileChange change = new BuildFileChange(Toolchain.NODE, POM, "", List.of());

    assertThatThrownBy(() -> router.rollback(change))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("NODE");
  }

  @Test
  void missingChangeIsNotRolledBack() {
    router.rollback(null);
    router.rollback(new BuildFileChange(Toolchain.JVM, null, "", List.of()));

    assertThat(jvm.calls).isEmpty();
  }

  private static Project project(final BuildTool buildTool) {
    return new Project(Path.of("app"), Language.JAVA, buildTool, Framework.NONE);
  }

  private static final class RecordingAdapter implements BuildToolConfiguration {
    private final List<String> calls = new ArrayList<>();

    @Override
    public BuildFileChange apply(final Project project, final List<InitGateOption> gates) {
      calls.add("apply");
      return new BuildFileChange(Toolchain.JVM, POM, "", List.of());
    }

    @Override
    public PlannedFileChange plan(
        final Project project,
        final List<InitGateOption> gates,
        final QualityPreset preset,
        final boolean approved) {
      calls.add("plan");
      return null;
    }

    @Override
    public void rollback(final BuildFileChange change) {
      calls.add("rollback");
    }
  }
}
