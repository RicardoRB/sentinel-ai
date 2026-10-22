package dev.ward.application.init;

import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.doctor.OpenSpecIgnoreWarning;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;

/** Decides which {@code openspec/} directory a project's OpenSpec gates use. */
public final class OpenSpecDirectories {
  private final EnvironmentInspection environment;

  /** Creates the policy; {@code environment} reports which directories hold {@code openspec/}. */
  public OpenSpecDirectories(final EnvironmentInspection environment) {
    this.environment = environment;
  }

  /**
   * Returns the project-relative path to the repository root when {@code project} is nested, has no
   * {@code openspec/} of its own and the repository root has one, so OpenSpec gates run there;
   * otherwise {@code null}.
   */
  public String root(final Project project) {
    if (!project.nested()
        || environment.hasOpenSpecDirectory(project.root())
        || !environment.hasOpenSpecDirectory(project.repositoryRoot())) {
      return null;
    }
    return project.root().relativize(project.repositoryRoot()).toString().replace('\\', '/');
  }

  /** Returns the warning for enabled OpenSpec validations whose directory Git ignores. */
  public List<String> warnings(final Project project, final List<InitGateOption> gates) {
    final boolean openSpec =
        gates.stream().anyMatch(gate -> InitSetupCatalog.OPENSPEC_VALIDATIONS.contains(gate.id()));
    final Path directory = root(project) == null ? project.root() : project.repositoryRoot();
    return openSpec && environment.isOpenSpecDirectoryIgnored(directory)
        ? List.of(OpenSpecIgnoreWarning.MESSAGE)
        : List.of();
  }
}
