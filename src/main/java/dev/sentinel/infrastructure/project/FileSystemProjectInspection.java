package dev.sentinel.infrastructure.project;

import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import dev.sentinel.domain.project.ProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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

  @Override
  public Optional<Project> detect(Path start) {
    for (Path dir = start.toAbsolutePath().normalize(); dir != null; dir = dir.getParent()) {
      Path pom = dir.resolve(POM);
      if (Files.isRegularFile(pom)) {
        return Optional.of(
            new Project(
                dir,
                Language.JAVA,
                BuildTool.MAVEN,
                detectFramework(pom),
                Files.isRegularFile(dir.resolve("mvnw"))));
      }
    }
    return Optional.empty();
  }

  @Override
  public List<Project> discover(Path start) {
    Path root = start.toAbsolutePath().normalize();
    Path candidate = root;
    Path parent;
    while ((parent = candidate.getParent()) != null
        && !Files.exists(candidate.resolve(".git"))
        && !Files.exists(candidate.resolve(POM))) {
      candidate = parent;
    }
    if (Files.exists(candidate.resolve(".git")) || Files.exists(candidate.resolve(POM)))
      root = candidate;
    List<Project> projects = new ArrayList<>();
    try (var paths = Files.walk(root, 4)) {
      paths.filter(Files::isDirectory).forEach(dir -> addMarkers(dir, projects));
    } catch (IOException e) {
      throw new IllegalStateException("Could not scan project roots: " + e.getMessage(), e);
    }
    return projects.stream()
        .distinct()
        .sorted(
            Comparator.comparing(Project::root).thenComparing(project -> project.language().name()))
        .toList();
  }

  private static void addMarkers(Path dir, List<Project> projects) {
    if (Files.isRegularFile(dir.resolve(POM))) {
      projects.add(
          new Project(
              dir,
              Language.JAVA,
              BuildTool.MAVEN,
              detectFramework(dir.resolve(POM)),
              Files.isRegularFile(dir.resolve("mvnw"))));
    } else if (Files.isRegularFile(dir.resolve("build.gradle"))
        || Files.isRegularFile(dir.resolve("build.gradle.kts"))) {
      projects.add(
          new Project(
              dir,
              Files.isRegularFile(dir.resolve("build.gradle.kts"))
                  ? Language.KOTLIN
                  : Language.JAVA,
              BuildTool.GRADLE,
              Framework.NONE));
    } else if (Files.isRegularFile(dir.resolve("tsconfig.json"))) {
      projects.add(new Project(dir, Language.TYPESCRIPT, tool(dir), Framework.NONE));
    } else if (Files.isRegularFile(dir.resolve("package.json"))) {
      projects.add(new Project(dir, Language.JAVASCRIPT, tool(dir), Framework.NONE));
    } else if (Files.isRegularFile(dir.resolve("pyproject.toml"))
        || Files.isRegularFile(dir.resolve("requirements.txt"))) {
      projects.add(
          new Project(
              dir,
              Language.PYTHON,
              Files.isRegularFile(dir.resolve("pyproject.toml")) ? BuildTool.POETRY : BuildTool.PIP,
              Framework.NONE));
    } else if (Files.isRegularFile(dir.resolve("go.mod"))) {
      projects.add(new Project(dir, Language.GO, BuildTool.GO, Framework.NONE));
    } else if (Files.isRegularFile(dir.resolve("Cargo.toml"))) {
      projects.add(new Project(dir, Language.RUST, BuildTool.CARGO, Framework.NONE));
    } else {
      try (var files = Files.list(dir)) {
        if (files.anyMatch(path -> path.getFileName().toString().endsWith(".csproj"))) {
          projects.add(new Project(dir, Language.CSHARP, BuildTool.DOTNET, Framework.NONE));
        }
      } catch (IOException ignored) {
        // An unreadable directory contributes no project marker.
      }
    }
  }

  private static BuildTool tool(Path dir) {
    if (Files.isRegularFile(dir.resolve("pnpm-lock.yaml"))) return BuildTool.PNPM;
    if (Files.isRegularFile(dir.resolve("yarn.lock"))) return BuildTool.YARN;
    return BuildTool.NPM;
  }

  /** Spring Boot is detected when the POM references any org.springframework.boot groupId. */
  private static Framework detectFramework(Path pom) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setNamespaceAware(false);
      DocumentBuilder builder = factory.newDocumentBuilder();
      builder.setErrorHandler(null);
      Document document = builder.parse(pom.toFile());
      NodeList groupIds = document.getElementsByTagName("groupId");
      for (int i = 0; i < groupIds.getLength(); i++) {
        if (SPRING_BOOT_GROUP_ID.equals(groupIds.item(i).getTextContent().trim())) {
          return Framework.SPRING_BOOT;
        }
      }
    } catch (javax.xml.parsers.ParserConfigurationException | SAXException | IOException e) {
      // A POM remains a Maven marker even when its framework cannot be identified.
    }
    return Framework.NONE;
  }
}
