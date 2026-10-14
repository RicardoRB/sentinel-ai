package dev.sentinel.infrastructure.traceability;

import dev.sentinel.domain.traceability.MappingObservation;
import dev.sentinel.domain.traceability.TestMappingInventory;
import dev.sentinel.domain.traceability.TraceabilityInventory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.inject.Inject;

/** Bounded Java source scanner for direct JUnit traceability annotations. */
public final class JavaTestMappingInventory implements TestMappingInventory {
  private static final Pattern METHOD =
      Pattern.compile(
          "(?:[A-Za-z_$][\\w$]*\\s+)*[A-Za-z_$][\\w$]*\\s*\\([^;{}]*\\)\\s*(?:throws\\s+[^{}]+)?\\{");

  @Inject
  public JavaTestMappingInventory() {}

  @Override
  public TraceabilityInventory<MappingObservation> inspect(final Path projectRoot) {
    final List<MappingObservation> observations = new ArrayList<>();
    final List<String> failures = new ArrayList<>();
    final List<Path> roots =
        List.of(
            projectRoot.resolve("src/test/java"),
            projectRoot.resolve("src/test").resolve("kotlin"));
    for (final Path root : roots) {
      if (!Files.isDirectory(root)) {
        continue;
      }
      try (Stream<Path> files = Files.walk(root)) {
        files
            .filter(path -> path.toString().endsWith(".java"))
            .sorted()
            .forEach(path -> scan(path, observations, failures));
      } catch (IOException e) {
        failures.add(root.toString());
      }
    }
    observations.sort(Comparator.comparing(MappingObservation::location));
    return new TraceabilityInventory<>(observations, failures);
  }

  private static void scan(
      final Path path, final List<MappingObservation> output, final List<String> failures) {
    final String source;
    try {
      source = Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException e) {
      failures.add(path.toString());
      return;
    }
    final String clean = JavaSourceSanitizer.mask(source);
    final Matcher methods = METHOD.matcher(clean);
    while (methods.find()) {
      final String prefix =
          clean.substring(Math.max(0, previousBoundary(clean, methods.start())), methods.start());
      final int line = lineAt(source, methods.start());
      final MappingObservation observation = JavaMappingObservationParser.parse(prefix, path, line);
      if (observation != null) {
        output.add(observation);
      }
    }
  }

  private static int previousBoundary(final String source, final int start) {
    int boundary = start;
    while (boundary > 0) {
      final char c = source.charAt(boundary - 1);
      if (c == ';' || c == '}' || c == '{') {
        break;
      }
      boundary--;
    }
    return boundary;
  }

  private static int lineAt(final String source, final int offset) {
    return (int) source.substring(0, offset).chars().filter(c -> c == '\n').count() + 1;
  }
}
