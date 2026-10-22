package dev.ward.infrastructure.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.ArchitectureTestChange;
import dev.ward.domain.init.ArchitectureTestGeneration;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.inject.Inject;

/** Creates a conservative, user-owned ArchUnit test only when the project has none. */
public final class ArchitectureTestGenerator implements ArchitectureTestGeneration {
  private static final Pattern PACKAGE = Pattern.compile("\\bpackage\\s+([A-Za-z_][\\w.]*)\\s*;");
  private static final String DEFAULT_PACKAGE = "com.example";
  private static final Set<String> LAYERS =
      Set.of(
          "domain",
          "application",
          "infrastructure",
          "adapter",
          "adapters",
          "core",
          "controller",
          "service",
          "repository",
          "model",
          "api",
          "web",
          "persistence");

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public ArchitectureTestGenerator() {}

  @Override
  public List<String> existingArchUnitTests(final Project project) {
    final List<String> names = new ArrayList<>();
    final List<Path> roots = new ArrayList<>();
    roots.add(project.root().resolve("src/test/java"));
    if (project.buildTool() == BuildTool.GRADLE) {
      roots.add(project.root().resolve("src/test/kotlin"));
    }
    for (final Path root : roots) {
      if (!Files.isDirectory(root)) {
        continue;
      }
      try (Stream<Path> files = Files.walk(root)) {
        files
            .filter(Files::isRegularFile)
            .filter(file -> String.valueOf(file.getFileName()).matches(".*Tests?\\.(java|kt)$"))
            .forEach(
                file -> {
                  try {
                    final String source = Files.readString(file);
                    if (source.contains("com.tngtech.archunit")
                        || source.contains("@AnalyzeClasses")) {
                      final String fileName = String.valueOf(file.getFileName());
                      names.add(fileName.substring(0, fileName.lastIndexOf('.')));
                    }
                  } catch (IOException e) {
                    throw new WardException(
                        "Could not inspect ArchUnit tests: " + e.getMessage(), e);
                  }
                });
      } catch (IOException e) {
        throw new WardException("Could not inspect test sources: " + e.getMessage(), e);
      }
    }
    return names.stream().distinct().sorted().toList();
  }

  @Override
  public ArchitectureTestChange apply(final Project project, final String architecture) {
    return apply(project, architecture, new QualityPreset.ArchitectureExtras(false, false, false));
  }

  @Override
  public ArchitectureTestChange apply(
      final Project project,
      final String architecture,
      final QualityPreset.ArchitectureExtras extras) {
    final String basePackage = detectBasePackage(project.root());
    final Path test = target(project.root(), basePackage);
    final Path packageDirectory = test.getParent();
    if (Files.exists(test) || !existingArchUnitTests(project).isEmpty()) {
      return new ArchitectureTestChange(test, false, architecture);
    }
    final String content = ArchitectureTestSource.render(basePackage, architecture, extras);
    try {
      Files.createDirectories(packageDirectory);
      Files.writeString(test, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
      return new ArchitectureTestChange(test, true, architecture);
    } catch (FileAlreadyExistsException e) {
      return new ArchitectureTestChange(test, false, architecture);
    } catch (IOException e) {
      throw new WardException(
          "Could not generate ArchUnit test " + test + ": " + e.getMessage(), e);
    }
  }

  @Override
  public PlannedFileChange plan(
      final Project project,
      final String architecture,
      final QualityPreset.ArchitectureExtras extras) {
    final Path test = target(project.root(), detectBasePackage(project.root()));
    return new PlannedFileChange(
        test,
        Files.exists(test) ? PlannedFileChange.Action.UNCHANGED : PlannedFileChange.Action.CREATE,
        List.of(architecture + " architecture"));
  }

  /** Where the generated test lives; shared by apply and plan. */
  private static Path target(final Path projectRoot, final String basePackage) {
    return projectRoot
        .resolve("src/test/java")
        .resolve(basePackage.replace('.', '/'))
        .resolve("ArchitectureTest.java");
  }

  @Override
  public void rollback(final ArchitectureTestChange change) {
    if (change == null || !change.created()) {
      return;
    }
    try {
      Files.deleteIfExists(change.file());
    } catch (IOException e) {
      throw new WardException(
          "Could not remove generated ArchUnit test " + change.file() + ": " + e.getMessage(), e);
    }
  }

  /**
   * The deepest package shared by every main source, so the test analyzes the whole application
   * rather than the package of whichever file is listed first. A prefix that ends in a layer name
   * means only that layer exists yet, so its parent is used.
   */
  private static String detectBasePackage(final Path root) {
    List<String> common = null;
    for (final List<String> sourcePackage : sourcePackages(root.resolve("src/main/java"))) {
      common = commonPrefix(common, sourcePackage);
    }
    if (common != null && common.size() > 1 && LAYERS.contains(common.getLast())) {
      common = common.subList(0, common.size() - 1);
    }
    return common == null || common.isEmpty() ? DEFAULT_PACKAGE : String.join(".", common);
  }

  /** The declared package of each main source, as name segments. */
  private static List<List<String>> sourcePackages(final Path source) {
    if (!Files.isDirectory(source)) {
      return List.of();
    }
    final List<List<String>> packages = new ArrayList<>();
    try (Stream<Path> files = Files.walk(source)) {
      for (final Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        final Matcher matcher = PACKAGE.matcher(Files.readString(file));
        if (matcher.find()) {
          packages.add(List.of(matcher.group(1).split("\\.")));
        }
      }
    } catch (IOException e) {
      throw new WardException("Could not inspect Java packages: " + e.getMessage(), e);
    }
    return packages;
  }

  private static List<String> commonPrefix(final List<String> current, final List<String> next) {
    if (current == null) {
      return next;
    }
    int shared = 0;
    while (shared < Math.min(current.size(), next.size())
        && current.get(shared).equals(next.get(shared))) {
      shared++;
    }
    return current.subList(0, shared);
  }
}
