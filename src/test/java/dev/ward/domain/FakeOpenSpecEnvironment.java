package dev.ward.domain;

import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.doctor.ExecutableObservation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/** Reports every executable as available or not, and checks the real openspec/ directory. */
public final class FakeOpenSpecEnvironment implements EnvironmentInspection {
  private final boolean available;

  /** Creates an environment where the OpenSpec executable is usable only when {@code available}. */
  public FakeOpenSpecEnvironment(final boolean available) {
    this.available = available;
  }

  @Override
  public EnvironmentFacts inspect(final Path projectRoot) {
    return new EnvironmentFacts(Set.of(), Map.of());
  }

  @Override
  public ExecutableObservation inspectExecutable(final Path projectRoot, final String requested) {
    return new ExecutableObservation(
        requested, Path.of(requested), available, available, available);
  }

  @Override
  public boolean hasOpenSpecDirectory(final Path projectRoot) {
    return Files.isDirectory(projectRoot.resolve("openspec"));
  }
}
