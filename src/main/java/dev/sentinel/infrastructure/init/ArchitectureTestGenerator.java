package dev.sentinel.infrastructure.init;

import dev.sentinel.domain.agent.PlannedFileChange;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.ArchitectureTestChange;
import dev.sentinel.domain.init.ArchitectureTestGeneration;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Project;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.inject.Inject;

/** Creates a conservative, user-owned ArchUnit test only when the project has none. */
public final class ArchitectureTestGenerator implements ArchitectureTestGeneration {
  private static final Pattern PACKAGE = Pattern.compile("\\bpackage\\s+([A-Za-z_][\\w.]*)\\s*;");

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
            .filter(file -> file.getFileName().toString().matches(".*Tests?\\.(java|kt)$"))
            .forEach(
                file -> {
                  try {
                    final String source = Files.readString(file);
                    if (source.contains("com.tngtech.archunit")
                        || source.contains("@AnalyzeClasses")) {
                      final String fileName = file.getFileName().toString();
                      names.add(fileName.substring(0, fileName.lastIndexOf('.')));
                    }
                  } catch (IOException e) {
                    throw new SentinelException(
                        "Could not inspect ArchUnit tests: " + e.getMessage(), e);
                  }
                });
      } catch (IOException e) {
        throw new SentinelException("Could not inspect test sources: " + e.getMessage(), e);
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
    final String content = testSource(basePackage, architecture, extras);
    try {
      Files.createDirectories(packageDirectory);
      Files.writeString(test, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
      return new ArchitectureTestChange(test, true, architecture);
    } catch (FileAlreadyExistsException e) {
      return new ArchitectureTestChange(test, false, architecture);
    } catch (IOException e) {
      throw new SentinelException(
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
      throw new SentinelException(
          "Could not remove generated ArchUnit test " + change.file() + ": " + e.getMessage(), e);
    }
  }

  private static String detectBasePackage(final Path root) {
    final Path source = root.resolve("src/main/java");
    if (Files.isDirectory(source)) {
      try (Stream<Path> files = Files.walk(source)) {
        for (final Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
          final Matcher matcher = PACKAGE.matcher(Files.readString(file));
          if (matcher.find()) {
            return matcher.group(1);
          }
        }
      } catch (IOException e) {
        throw new SentinelException("Could not inspect Java packages: " + e.getMessage(), e);
      }
    }
    return "com.example";
  }

  private static String testSource(
      final String basePackage,
      final String architecture,
      final QualityPreset.ArchitectureExtras extras) {
    final String rule =
        switch (architecture) {
          case "hexagonal" ->
              "noClasses().that().resideInAnyPackage(\"..domain..\", \"..application..\")"
                  + ".should().dependOnClassesThat().resideInAnyPackage(\"..adapter..\", \"..infrastructure..\")";
          case "clean" ->
              "noClasses().that().resideInAnyPackage(\"..domain..\", \"..core..\")"
                  + ".should().dependOnClassesThat().resideInAnyPackage(\"..controller..\", \"..adapter..\", \"..infrastructure..\")";
          default ->
              "noClasses().that().resideInAnyPackage(\"..controller..\")"
                  + ".should().dependOnClassesThat().resideInAnyPackage(\"..repository..\")";
        };
    return "package "
        + basePackage
        + ";\n\n"
        + "import com.tngtech.archunit.junit.AnalyzeClasses;\n"
        + "import com.tngtech.archunit.junit.ArchTest;\n"
        + "import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;\n"
        + "\n"
        + "// Generated by Sentinel for the "
        + architecture
        + " architecture.\n"
        + "@AnalyzeClasses(packages = \""
        + basePackage
        + "\", importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class)\n"
        + "class ArchitectureTest {\n"
        + "    @ArchTest\n"
        + "    static final com.tngtech.archunit.lang.ArchRule architecture = "
        + rule
        + ";\n"
        + (extras.cycles()
            ? "    @ArchTest\n    static final com.tngtech.archunit.lang.ArchRule noCycles = com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices().matching(\""
                + basePackage
                + ".(*)..\").should().beFreeOfCycles();\n"
            : "")
        + (extras.noFieldInjection()
            ? "    @ArchTest\n    static final com.tngtech.archunit.lang.ArchRule noFieldInjection = com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;\n"
            : "")
        + (extras.noStandardStreams()
            ? "    @ArchTest\n    static final com.tngtech.archunit.lang.ArchRule noStandardStreams = com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;\n"
            : "")
        + "}\n";
  }
}
