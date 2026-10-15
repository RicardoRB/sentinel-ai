package dev.sentinel.infrastructure.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.fory.json.annotation.JsonType;
import org.junit.jupiter.api.Test;

class NativeJsonCodecMetadataTest {
  private static final String NATIVE_IMAGE_PROPERTIES =
      "META-INF/native-image/dev.sentinel/sentinel-cli/native-image.properties";
  private static final String BUILD_TIME_INIT = "--initialize-at-build-time=";

  @Test
  void everyJsonTypePackageIsInitializedAtBuildTime() throws IOException {
    final Set<String> buildTimePackages = buildTimeInitializedPackages();

    final Set<String> missing =
        jsonTypePackages().stream()
            .filter(name -> !buildTimePackages.contains(name))
            .collect(Collectors.toSet());

    assertThat(jsonTypePackages()).isNotEmpty();
    assertThat(missing)
        .as("@JsonType packages missing from --initialize-at-build-time in native-image.properties")
        .isEmpty();
  }

  private static Set<String> jsonTypePackages() {
    return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("dev.sentinel")
            .stream()
            .filter(javaClass -> javaClass.isAnnotatedWith(JsonType.class))
            .map(JavaClass::getPackageName)
            .collect(Collectors.toSet());
  }

  private static Set<String> buildTimeInitializedPackages() throws IOException {
    final String argsLine;
    try (InputStream stream =
        Thread.currentThread()
            .getContextClassLoader()
            .getResourceAsStream(NATIVE_IMAGE_PROPERTIES)) {
      assertThat(stream).as(NATIVE_IMAGE_PROPERTIES).isNotNull();
      final String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      argsLine =
          content
              .replace("\\\n", " ")
              .lines()
              .filter(line -> line.startsWith("Args=") && line.contains(BUILD_TIME_INIT))
              .findFirst()
              .orElseThrow(() -> new AssertionError("no " + BUILD_TIME_INIT + " in Args"));
    }
    final String packages =
        argsLine.substring(argsLine.indexOf(BUILD_TIME_INIT) + BUILD_TIME_INIT.length());
    return Arrays.stream(packages.split("[,\\s]+"))
        .takeWhile(entry -> !entry.startsWith("--"))
        .filter(entry -> !entry.isEmpty())
        .collect(Collectors.toSet());
  }
}
