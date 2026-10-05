package dev.sentinel.infrastructure.doctor;

import dev.sentinel.domain.doctor.EnvironmentFacts;
import dev.sentinel.domain.doctor.EnvironmentInspection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Reads executable availability and prerequisite markers from the current machine. */
public final class SystemEnvironmentInspection implements EnvironmentInspection {
    private static final Set<String> PROBED_EXECUTABLES = Set.of("mvn", "java", "git", "claude", "opencode");

    @Override
    public EnvironmentFacts inspect(Path projectRoot) {
        Set<String> available = new HashSet<>();
        String path = System.getenv("PATH");
        if (path != null) {
            Arrays.stream(path.split(java.io.File.pathSeparator))
                    .map(Path::of)
                    .forEach(directory -> PROBED_EXECUTABLES.stream()
                            .filter(name -> Files.isExecutable(directory.resolve(name)))
                            .forEach(available::add));
        }
        Map<String, String> values = Map.of(
                "mavenWrapperAvailable", Boolean.toString(Files.isExecutable(projectRoot.resolve("mvnw"))),
                "configurationPresent", Boolean.toString(Files.isRegularFile(projectRoot.resolve("sentinel.toml"))),
                "openCodeCommandPresent", Boolean.toString(Files.isRegularFile(
                        projectRoot.resolve(".opencode/commands/sentinel-check.md"))),
                "nativeExecutablePresent", Boolean.toString(Files.isExecutable(Path.of("target/sentinel"))));
        return new EnvironmentFacts(available, values);
    }
}
