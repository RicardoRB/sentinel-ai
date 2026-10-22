package dev.ward.infrastructure.traceability;

import dev.ward.domain.traceability.TraceabilityPrerequisites;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;

/** Checks the traceability prerequisites on the file system. */
public final class FileSystemTraceabilityPrerequisites implements TraceabilityPrerequisites {
  /** Creates the stateless adapter that Dagger injects. */
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
