package dev.sentinel.infrastructure.init;

import dev.sentinel.domain.agent.PlannedFileChange;
import dev.sentinel.domain.init.BuildToolConfiguration;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.PomChange;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;

/** Routes initialization mutations to the adapter for the detected build tool. */
public final class BuildToolConfigurator implements BuildToolConfiguration {
  private final PomToolConfigurator maven;
  private final GradleToolConfigurator gradle;

  @Inject
  public BuildToolConfigurator(
      final PomToolConfigurator maven, final GradleToolConfigurator gradle) {
    this.maven = maven;
    this.gradle = gradle;
  }

  @Override
  public PomChange apply(final Project project, final List<InitGateOption> gates) {
    return adapter(project).apply(project, gates);
  }

  @Override
  public PomChange apply(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Object approval) {
    return adapter(project).apply(project, gates, preset, approval);
  }

  @Override
  public PlannedFileChange plan(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean approved) {
    return adapter(project).plan(project, gates, preset, approved);
  }

  @Override
  public void rollback(final PomChange change) {
    if (change == null) {
      return;
    }
    final Path file = change.file();
    if (file == null) {
      return;
    }
    final String fileName = file.toString();
    if (fileName.endsWith("/build.gradle") || fileName.endsWith("/build.gradle.kts")) {
      gradle.rollback(change);
    } else {
      maven.rollback(change);
    }
  }

  private BuildToolConfiguration adapter(final Project project) {
    return project.buildTool() == BuildTool.GRADLE ? gradle : maven;
  }
}
