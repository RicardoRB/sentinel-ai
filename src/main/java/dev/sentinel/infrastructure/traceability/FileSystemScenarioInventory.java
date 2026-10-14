package dev.sentinel.infrastructure.traceability;

import dev.sentinel.domain.traceability.ScenarioDefinition;
import dev.sentinel.domain.traceability.ScenarioIdentity;
import dev.sentinel.domain.traceability.ScenarioInventory;
import dev.sentinel.domain.traceability.SourceLocation;
import dev.sentinel.domain.traceability.TraceabilityInventory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.inject.Inject;

/** Reads only durable live specs, never active changes or archives. */
public final class FileSystemScenarioInventory implements ScenarioInventory {
  @Inject
  public FileSystemScenarioInventory() {}

  @Override
  public TraceabilityInventory<ScenarioDefinition> inspect(final Path projectRoot) {
    final Path specs = projectRoot.resolve("openspec/specs");
    if (!Files.isDirectory(specs)) {
      return new TraceabilityInventory<>(List.of(), List.of("openspec/specs"));
    }
    final List<ScenarioDefinition> scenarios = new ArrayList<>();
    final List<String> failures = new ArrayList<>();
    try (Stream<Path> files = Files.walk(specs)) {
      files
          .filter(path -> "spec.md".equals(path.getFileName().toString()))
          .sorted()
          .forEach(path -> read(path, specs, scenarios, failures));
    } catch (IOException e) {
      failures.add(specs.toString());
    }
    scenarios.sort(Comparator.naturalOrder());
    return new TraceabilityInventory<>(scenarios, failures);
  }

  private static void read(
      final Path path,
      final Path specs,
      final List<ScenarioDefinition> scenarios,
      final List<String> failures) {
    final List<String> lines;
    try {
      lines = Files.readAllLines(path, StandardCharsets.UTF_8);
    } catch (IOException e) {
      failures.add(path.toString());
      return;
    }
    final String capability =
        specs
            .relativize(path.getParent())
            .toString()
            .replace(path.getFileSystem().getSeparator(), "/");
    for (int i = 0; i < lines.size(); i++) {
      final String line = lines.get(i).trim();
      if (line.startsWith("### Scenario:") || line.startsWith("#### Scenario:")) {
        final String name = line.substring(line.indexOf(':') + 1).trim();
        if (!name.isBlank() && !capability.isBlank()) {
          scenarios.add(
              new ScenarioDefinition(
                  new ScenarioIdentity(capability, name), new SourceLocation(path, i + 1)));
        }
      }
    }
  }
}
