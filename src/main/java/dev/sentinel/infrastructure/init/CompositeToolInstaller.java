package dev.sentinel.infrastructure.init;

import dev.sentinel.domain.init.ToolInstallationResult;
import dev.sentinel.domain.init.ToolInstaller;
import java.nio.file.Path;
import javax.inject.Inject;

/** Routes project-local OpenSpec installation and gate-tool installation to their adapters. */
public final class CompositeToolInstaller implements ToolInstaller {
  private static final String OPENSPEC = "openspec";
  private final OpenSpecToolInstaller openSpec;
  private final PackageManagerToolInstaller gateTools;

  /** Creates the installer that delegates OpenSpec and gate executables to their installers. */
  @Inject
  public CompositeToolInstaller(
      final OpenSpecToolInstaller openSpec, final PackageManagerToolInstaller gateTools) {
    this.openSpec = openSpec;
    this.gateTools = gateTools;
  }

  @Override
  public ToolInstallationResult install(final Path projectRoot, final String tool) {
    return OPENSPEC.equals(tool)
        ? openSpec.install(projectRoot, tool)
        : gateTools.install(projectRoot, tool);
  }

  @Override
  public void rollback(final Path projectRoot, final ToolInstallationResult result) {
    if (OPENSPEC.equals(result.tool())) {
      openSpec.rollback(projectRoot, result);
    }
  }

  @Override
  public void rollbackPath(final Path projectRoot, final Path executable) {
    openSpec.rollbackPath(projectRoot, executable);
  }
}
