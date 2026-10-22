package dev.ward.infrastructure.project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Decides whether a build directory is a module of an enclosing Maven or Gradle aggregator. */
final class BuildModules {
  private static final String MODULE = "module";
  private static final String POM_SUFFIX = "/" + FileSystemProjectInspection.POM;
  private static final Set<String> GRADLE_SETTINGS =
      Set.of("settings.gradle", "settings.gradle.kts");
  private static final Set<String> GRADLE_BUILDS = Set.of("build.gradle", "build.gradle.kts");

  private BuildModules() {}

  /**
   * Returns whether {@code candidate} belongs to the build at {@code aggregator}: listed in its
   * Maven {@code <modules>}, including profile modules, or below its Gradle settings file.
   */
  static boolean isModuleOf(final Path aggregator, final Path candidate) {
    if (aggregator.equals(candidate) || !candidate.startsWith(aggregator)) {
      return false;
    }
    // shortcut: any Gradle build below a settings file is a module, so a custom projectDir outside
    // the settings root is missed; read include() and projectDir if that layout shows up.
    return hasAny(aggregator, GRADLE_SETTINGS) && hasAny(candidate, GRADLE_BUILDS)
        || mavenModules(aggregator).contains(candidate);
  }

  /** Returns the module directories that the POM in {@code dir} lists, resolved and normalized. */
  static Set<Path> mavenModules(final Path dir) {
    final Path pom = dir.resolve(FileSystemProjectInspection.POM);
    if (!Files.isRegularFile(pom)) {
      return Set.of();
    }
    final Optional<Document> document = FileSystemProjectInspection.parse(pom);
    if (document.isEmpty()) {
      return Set.of();
    }
    final NodeList modules = document.get().getElementsByTagName(MODULE);
    final Set<Path> result = new HashSet<>();
    for (int i = 0; i < modules.getLength(); i++) {
      final Node module = modules.item(i);
      if (isModulesEntry(module)) {
        result.add(resolve(dir, module.getTextContent().trim()));
      }
    }
    return result;
  }

  /**
   * Counts {@code <modules><module>} only, so unrelated elements named {@code module} are skipped.
   */
  private static boolean isModulesEntry(final Node module) {
    final Node parent = module.getParentNode();
    return parent != null && "modules".equals(parent.getNodeName());
  }

  /** Maven accepts a module as a directory or as a path to its POM file. */
  private static Path resolve(final Path dir, final String module) {
    final String directory =
        module.endsWith(POM_SUFFIX)
            ? module.substring(0, module.length() - POM_SUFFIX.length())
            : module;
    return dir.resolve(directory).toAbsolutePath().normalize();
  }

  private static boolean hasAny(final Path dir, final Set<String> files) {
    return files.stream().anyMatch(file -> Files.isRegularFile(dir.resolve(file)));
  }
}
