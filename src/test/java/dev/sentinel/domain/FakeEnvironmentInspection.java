package dev.sentinel.domain;

import dev.sentinel.domain.doctor.EnvironmentFacts;
import dev.sentinel.domain.doctor.EnvironmentInspection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reports a fixed set of executables instead of probing the host PATH. */
public class FakeEnvironmentInspection implements EnvironmentInspection {

  public final List<Path> inspectedRoots = new ArrayList<>();
  private final EnvironmentFacts facts;

  public FakeEnvironmentInspection(final String... executables) {
    this.facts = new EnvironmentFacts(Set.of(executables), Map.of());
  }

  @Override
  public EnvironmentFacts inspect(final Path projectRoot) {
    inspectedRoots.add(projectRoot);
    return facts;
  }
}
