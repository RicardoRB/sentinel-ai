package dev.sentinel.infrastructure.traceability;

import dev.sentinel.domain.traceability.TraceabilityPrerequisites;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;

public final class FileSystemTraceabilityPrerequisites implements TraceabilityPrerequisites {
  @Inject
  public FileSystemTraceabilityPrerequisites() {}

  @Override
  public boolean liveSpecsDirectory(final Path projectRoot) {
    return Files.isDirectory(projectRoot.resolve("openspec/specs"));
  }

  @Override
  public boolean javaTestSourceDirectory(final Path projectRoot) {
    return Files.isDirectory(projectRoot.resolve("src/test/java"));
  }
}
