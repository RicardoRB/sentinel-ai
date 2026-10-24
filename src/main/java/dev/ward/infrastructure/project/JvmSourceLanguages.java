package dev.ward.infrastructure.project;

import dev.ward.domain.project.Language;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Finds the JVM languages of a Maven or Gradle project from its {@code src/main/java} and {@code
 * src/main/kotlin} directories, in the project and the modules below it. The build file says
 * nothing about the language: a Java project can use a Kotlin build script.
 */
final class JvmSourceLanguages {
  // Modules sit up to this many directories below the project, plus src/main/<language>.
  private static final int MAX_DEPTH = FileSystemProjectInspection.MAX_SEARCH_DEPTH + 3;
  private static final Map<String, Language> SOURCE_DIRECTORIES =
      Map.of("java", Language.JAVA, "kotlin", Language.KOTLIN);

  private JvmSourceLanguages() {}

  /**
   * Returns the languages found below {@code dir}. Java comes first when present; with no sources
   * the project counts as Java.
   */
  static Set<Language> of(final Path dir) {
    final Set<Language> found = EnumSet.noneOf(Language.class);
    // shortcut: a nested build that is not a module also counts; read the module list if mixed
    // repositories report a language they do not compile.
    try {
      Files.walkFileTree(
          dir,
          Set.of(),
          MAX_DEPTH,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(
                final Path path, final BasicFileAttributes attributes) {
              if (!path.equals(dir) && RepositoryProjectScan.skipped(path)) {
                return FileVisitResult.SKIP_SUBTREE;
              }
              final Language language = sourceLanguage(path);
              if (language != null) {
                found.add(language);
                return found.size() == SOURCE_DIRECTORIES.size()
                    ? FileVisitResult.TERMINATE
                    : FileVisitResult.SKIP_SUBTREE;
              }
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      throw new IllegalStateException("Could not inspect project sources in " + dir, e);
    }
    return found.isEmpty() ? EnumSet.of(Language.JAVA) : found;
  }

  /** Returns the primary language: Java unless the project has only Kotlin sources. */
  static Language primary(final Set<Language> languages) {
    return languages.contains(Language.JAVA) ? Language.JAVA : Language.KOTLIN;
  }

  private static Language sourceLanguage(final Path path) {
    final Path main = path.getParent();
    final Path src = main == null ? null : main.getParent();
    if (src == null
        || !"main".equals(String.valueOf(main.getFileName()))
        || !"src".equals(String.valueOf(src.getFileName()))) {
      return null;
    }
    return SOURCE_DIRECTORIES.get(String.valueOf(path.getFileName()));
  }
}
