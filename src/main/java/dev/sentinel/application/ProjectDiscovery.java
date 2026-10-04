package dev.sentinel.application;

import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Discovers independent project roots without collapsing a mixed repository to one language. */
public final class ProjectDiscovery {
    public List<Project> discover(Path start) {
        Path root = start.toAbsolutePath().normalize();
        Path candidate = root;
        while (candidate.getParent() != null && !Files.exists(candidate.resolve(".git"))
                && !Files.exists(candidate.resolve("pom.xml"))) candidate = candidate.getParent();
        if (Files.exists(candidate.resolve(".git")) || Files.exists(candidate.resolve("pom.xml"))) root = candidate;
        List<Project> projects = new ArrayList<>();
        try (var paths = Files.walk(root, 4)) {
            paths.filter(Files::isDirectory).forEach(dir -> addMarkers(dir, projects));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not scan project roots: " + e.getMessage(), e);
        }
        return projects.stream().distinct().sorted(Comparator.comparing(Project::root)
                .thenComparing(project -> project.language().name())).toList();
    }

    private static void addMarkers(Path dir, List<Project> projects) {
        if (Files.isRegularFile(dir.resolve("pom.xml"))) projects.add(new Project(dir, Language.JAVA, BuildTool.MAVEN, Framework.NONE));
        else if (Files.isRegularFile(dir.resolve("build.gradle")) || Files.isRegularFile(dir.resolve("build.gradle.kts")))
            projects.add(new Project(dir, Files.isRegularFile(dir.resolve("build.gradle.kts")) ? Language.KOTLIN : Language.JAVA, BuildTool.GRADLE, Framework.NONE));
        else if (Files.isRegularFile(dir.resolve("tsconfig.json"))) projects.add(new Project(dir, Language.TYPESCRIPT, tool(dir), Framework.NONE));
        else if (Files.isRegularFile(dir.resolve("package.json"))) projects.add(new Project(dir, Language.JAVASCRIPT, tool(dir), Framework.NONE));
        else if (Files.isRegularFile(dir.resolve("pyproject.toml")) || Files.isRegularFile(dir.resolve("requirements.txt")))
            projects.add(new Project(dir, Language.PYTHON, Files.isRegularFile(dir.resolve("pyproject.toml")) ? BuildTool.POETRY : BuildTool.PIP, Framework.NONE));
        else if (Files.isRegularFile(dir.resolve("go.mod"))) projects.add(new Project(dir, Language.GO, BuildTool.GO, Framework.NONE));
        else if (Files.isRegularFile(dir.resolve("Cargo.toml"))) projects.add(new Project(dir, Language.RUST, BuildTool.CARGO, Framework.NONE));
        else try (var files = Files.list(dir)) {
            if (files.anyMatch(path -> path.getFileName().toString().endsWith(".csproj")))
                projects.add(new Project(dir, Language.CSHARP, BuildTool.DOTNET, Framework.NONE));
        } catch (java.io.IOException ignored) { }
    }

    private static BuildTool tool(Path dir) {
        if (Files.isRegularFile(dir.resolve("pnpm-lock.yaml"))) return BuildTool.PNPM;
        if (Files.isRegularFile(dir.resolve("yarn.lock"))) return BuildTool.YARN;
        return BuildTool.NPM;
    }
}
