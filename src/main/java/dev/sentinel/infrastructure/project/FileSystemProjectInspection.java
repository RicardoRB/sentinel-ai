package dev.sentinel.infrastructure.project;

import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import dev.sentinel.domain.project.ProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Filesystem-backed project marker scanning and secure Maven metadata inspection. */
public final class FileSystemProjectInspection implements ProjectInspection {
  public static final String POM = "pom.xml";
  private static final String SPRING_BOOT_GROUP_ID = "org.springframework.boot";

  /** Build-file markers in precedence order; the first match decides a directory's project. */
  private static final List<Marker> MARKERS =
      List.of(
          new Marker(POM, FileSystemProjectInspection::mavenProject),
          new Marker(
              "build.gradle.kts",
              dir -> new Project(dir, Language.KOTLIN, BuildTool.GRADLE, Framework.NONE)),
          new Marker(
              "build.gradle",
              dir -> new Project(dir, Language.JAVA, BuildTool.GRADLE, Framework.NONE)),
          new Marker(
              "tsconfig.json",
              dir -> new Project(dir, Language.TYPESCRIPT, tool(dir), Framework.NONE)),
          new Marker(
              "package.json",
              dir -> new Project(dir, Language.JAVASCRIPT, tool(dir), Framework.NONE)),
          new Marker(
              "pyproject.toml",
              dir -> new Project(dir, Language.PYTHON, BuildTool.POETRY, Framework.NONE)),
          new Marker(
              "requirements.txt",
              dir -> new Project(dir, Language.PYTHON, BuildTool.PIP, Framework.NONE)),
          new Marker("go.mod", dir -> new Project(dir, Language.GO, BuildTool.GO, Framework.NONE)),
          new Marker(
              "Cargo.toml",
              dir -> new Project(dir, Language.RUST, BuildTool.CARGO, Framework.NONE)));

  @Inject
  public FileSystemProjectInspection() {}

  @Override
  public Optional<Project> detect(final Path start) {
    for (Path dir = start.toAbsolutePath().normalize(); dir != null; dir = dir.getParent()) {
      if (Files.isRegularFile(dir.resolve(POM))) {
        return Optional.of(mavenProject(dir));
      }
    }
    return Optional.empty();
  }

  @Override
  public List<Project> discover(final Path start) {
    Path root = start.toAbsolutePath().normalize();
    Path candidate = root;
    Path parent = candidate.getParent();
    while (parent != null && !isRepositoryRoot(candidate)) {
      candidate = parent;
      parent = candidate.getParent();
    }
    if (isRepositoryRoot(candidate)) {
      root = candidate;
    }
    final List<Project> projects;
    try (Stream<Path> paths = Files.walk(root, 4)) {
      projects =
          paths
              .filter(Files::isDirectory)
              .map(FileSystemProjectInspection::projectAt)
              .flatMap(Optional::stream)
              .toList();
    } catch (IOException e) {
      throw new IllegalStateException("Could not scan project roots: " + e.getMessage(), e);
    }
    return projects.stream()
        .distinct()
        .sorted(
            Comparator.comparing(Project::root).thenComparing(project -> project.language().name()))
        .toList();
  }

  private static boolean isRepositoryRoot(final Path dir) {
    return Files.exists(dir.resolve(".git")) || Files.exists(dir.resolve(POM));
  }

  private static Optional<Project> projectAt(final Path dir) {
    for (final Marker marker : MARKERS) {
      if (Files.isRegularFile(dir.resolve(marker.file()))) {
        return Optional.of(marker.project().apply(dir));
      }
    }
    try (Stream<Path> files = Files.list(dir)) {
      return files.anyMatch(path -> path.getFileName().toString().endsWith(".csproj"))
          ? Optional.of(new Project(dir, Language.CSHARP, BuildTool.DOTNET, Framework.NONE))
          : Optional.empty();
    } catch (IOException e) {
      throw new IllegalStateException("Could not inspect project directory " + dir, e);
    }
  }

  private static Project mavenProject(final Path dir) {
    return new Project(
        dir,
        Language.JAVA,
        BuildTool.MAVEN,
        detectFramework(dir.resolve(POM)),
        Files.isRegularFile(dir.resolve("mvnw")));
  }

  private static BuildTool tool(final Path dir) {
    if (Files.isRegularFile(dir.resolve("pnpm-lock.yaml"))) {
      return BuildTool.PNPM;
    }
    if (Files.isRegularFile(dir.resolve("yarn.lock"))) {
      return BuildTool.YARN;
    }
    return BuildTool.NPM;
  }

  /** Spring Boot is detected when the POM references any org.springframework.boot groupId. */
  private static Framework detectFramework(final Path pom) {
    try {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setNamespaceAware(false);
      final DocumentBuilder builder = factory.newDocumentBuilder();
      builder.setErrorHandler(null);
      final Document document = builder.parse(pom.toFile());
      final NodeList groupIds = document.getElementsByTagName("groupId");
      for (int i = 0; i < groupIds.getLength(); i++) {
        if (SPRING_BOOT_GROUP_ID.equals(groupIds.item(i).getTextContent().trim())) {
          return Framework.SPRING_BOOT;
        }
      }
    } catch (javax.xml.parsers.ParserConfigurationException | SAXException | IOException e) {
      // A POM remains a Maven marker even when its framework cannot be identified.
      return Framework.NONE;
    }
    return Framework.NONE;
  }

  private record Marker(String file, Function<Path, Project> project) {}
}
