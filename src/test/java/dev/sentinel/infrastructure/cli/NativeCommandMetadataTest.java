package dev.sentinel.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.Command;

class NativeCommandMetadataTest {
  private static final String NATIVE_IMAGE_DIR = "META-INF/native-image/";
  private static final String GENERATED_REFLECT_CONFIG =
      NATIVE_IMAGE_DIR + "picocli-generated/dev.sentinel/sentinel-ai/reflect-config.json";

  @Test
  void generatedReflectionConfigListsEveryCommandClass() throws IOException {
    final String config = readResource(GENERATED_REFLECT_CONFIG);

    final Set<String> missing =
        commandClasses().stream()
            .filter(name -> !config.contains("\"name\" : \"" + name + "\""))
            .collect(Collectors.toSet());

    assertThat(commandClasses()).isNotEmpty();
    assertThat(missing).as("commands missing from generated reflect-config.json").isEmpty();
  }

  @Test
  void handWrittenMetadataDoesNotRelistCommandClasses() throws IOException {
    final Enumeration<URL> roots =
        getClass().getClassLoader().getResources(NATIVE_IMAGE_DIR + "dev.sentinel/sentinel-ai");
    final Set<String> commands = commandClasses();

    while (roots.hasMoreElements()) {
      final Path directory = Path.of(URI.create(roots.nextElement().toString()));
      try (Stream<Path> files = Files.list(directory)) {
        for (final Path file : files.filter(Files::isRegularFile).toList()) {
          final String content = Files.readString(file, StandardCharsets.UTF_8);
          assertThat(commands)
              .as("%s must not re-list generated Picocli commands", file.getFileName())
              .noneMatch(content::contains);
        }
      }
    }
  }

  private static Set<String> commandClasses() {
    return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("dev.sentinel.infrastructure.cli")
            .stream()
            .filter(javaClass -> javaClass.isAnnotatedWith(Command.class))
            .map(JavaClass::getName)
            .collect(Collectors.toSet());
  }

  private String readResource(String path) throws IOException {
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
      assertThat(stream).as(path).isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
