package dev.ward.domain.project;

import java.nio.file.Path;
import java.util.Locale;

/**
 * A detected project.
 *
 * @param root the project root directory
 * @param language its primary language
 * @param buildTool its build tool
 * @param framework its framework
 * @param mavenWrapperAvailable whether the build tool's wrapper, such as {@code mvnw}, is present
 * @param publishesArtifacts whether the Maven POM declares {@code <distributionManagement>}, the
 *     signal that a released library baseline exists
 * @param repositoryRoot the Git top-level containing the project, or {@code root} outside a Git
 *     work tree
 */
public record Project(
    Path root,
    Language language,
    BuildTool buildTool,
    Framework framework,
    boolean mavenWrapperAvailable,
    boolean publishesArtifacts,
    Path repositoryRoot) {
  /** Creates a project without a Maven Wrapper. */
  public Project(
      final Path root,
      final Language language,
      final BuildTool buildTool,
      final Framework framework) {
    this(root, language, buildTool, framework, false);
  }

  /** Creates a project that does not publish artifacts. */
  public Project(
      final Path root,
      final Language language,
      final BuildTool buildTool,
      final Framework framework,
      final boolean mavenWrapperAvailable) {
    this(root, language, buildTool, framework, mavenWrapperAvailable, false, root);
  }

  /** Returns this project inside the Git work tree whose top-level is {@code repository}. */
  public Project withRepositoryRoot(final Path repository) {
    return new Project(
        root,
        language,
        buildTool,
        framework,
        mavenWrapperAvailable,
        publishesArtifacts,
        repository);
  }

  /**
   * Returns the project root relative to the repository root with {@code /} separators, or an empty
   * string when the project root is the repository root.
   */
  public String projectPath() {
    return repositoryRoot.relativize(root).toString().replace('\\', '/');
  }

  /**
   * Returns the project path as a file-name fragment: lower case, with every run of characters
   * other than letters and digits replaced by {@code -}; empty for a root project.
   */
  public String pathSlug() {
    return slug(projectPath());
  }

  /** Returns {@code path} as a file-name fragment, as {@link #pathSlug()} does. */
  public static String slug(final String path) {
    return path.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
  }

  /**
   * Returns where a tool that {@code atRepositoryRoot} installs its files for this project: the
   * repository root for a nested project, else the project root.
   */
  public Path installRoot(final boolean atRepositoryRoot) {
    return atRepositoryRoot && nested() ? repositoryRoot : root;
  }

  /** Returns the project path a tool installed at {@link #installRoot} needs, or empty. */
  public String installPath(final boolean atRepositoryRoot) {
    return atRepositoryRoot && nested() ? projectPath() : "";
  }

  /** Returns whether the project sits below the repository root rather than at it. */
  public boolean nested() {
    return !projectPath().isEmpty();
  }

  /**
   * Returns whether an API-compatibility baseline can exist: Maven needs a released artifact, which
   * only projects declaring {@code <distributionManagement>} publish; Gradle configures its own.
   */
  public boolean hasApiBaseline() {
    return buildTool != BuildTool.MAVEN || publishesArtifacts;
  }

  /** Returns whether the wrapper belonging to this project's build tool is available. */
  public boolean wrapperAvailable() {
    return mavenWrapperAvailable;
  }

  /** Returns the wrapper-first executable name for this project's build tool. */
  public String buildExecutable() {
    return switch (buildTool) {
      case MAVEN -> mavenWrapperAvailable ? "./mvnw" : "mvn";
      case GRADLE -> mavenWrapperAvailable ? "./gradlew" : "gradle";
      default -> buildTool.displayName();
    };
  }
}
