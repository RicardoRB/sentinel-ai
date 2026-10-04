package dev.sentinel.application;

import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.project.Project;
import com.google.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class InitService {

    static final String DEFAULT_CONFIGURATION = """
            version = 1

            [quality-gates.tests]
            enabled = true
            command = "./mvnw test"
            """;

    public record InitResult(Path file, boolean created) {
    }

    private final ProjectDetector detector;

    @Inject
    public InitService(ProjectDetector detector) {
        this.detector = detector;
    }

    public InitResult init(Path start) {
        Project project = detector.detect(start).orElseThrow(ProjectNotFoundException::new);
        Path file = project.root().resolve(SentinelConfiguration.FILE_NAME);
        try {
            // CREATE_NEW never overwrites, even if the file appears between check and write.
            Files.writeString(file, DEFAULT_CONFIGURATION, StandardOpenOption.CREATE_NEW);
            return new InitResult(file, true);
        } catch (java.nio.file.FileAlreadyExistsException e) {
            return new InitResult(file, false);
        } catch (IOException e) {
            throw new SentinelException("Could not write " + file + ": " + e.getMessage(), e);
        }
    }
}
