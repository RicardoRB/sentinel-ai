package dev.sentinel.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SuppressionSourceDetectorTest {
  @Test
  void detectsSimpleQualifiedAndMultilineAnnotationsAndNopmd() {
    final String source =
        "@SuppressWarnings(\n\"PMD.X\")\n"
            + "@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(\"X\")\n"
            + "// NOPMD\n"
            + "/* NOPMD */\n";

    assertThat(SuppressionSourceDetector.find(source))
        .extracting(SuppressionSourceDetector.Diagnostic::mechanism)
        .containsExactly(
            "SuppressWarnings",
            "edu.umd.cs.findbugs.annotations.SuppressFBWarnings",
            "NOPMD",
            "NOPMD");
  }

  @Test
  void ignoresStringsTextBlocksAndDocumentationMentions() {
    final String source =
        "// Documentation mentions @SuppressWarnings as data\n"
            + "String text = \"@SuppressWarnings NOPMD\";\n"
            + "String block = \"\"\"@SuppressFBWarnings NOPMD\"\"\";\n";

    assertThat(SuppressionSourceDetector.find(source)).isEmpty();
  }

  @Test
  void scansBothHandwrittenSourceRootsAndFailsClearlyWhenMissing() throws IOException {
    assertThat(findSources(Path.of("src/main/java"), Path.of("src/test/java"))).isEmpty();
    assertThatThrownBy(() -> findSources(Path.of("missing-main"), Path.of("missing-test")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("source root");
  }

  @Test
  void reportsFixtureFileLineAndMechanism(@TempDir final Path temporaryDirectory)
      throws IOException {
    final Path main = Files.createDirectories(temporaryDirectory.resolve("main"));
    final Path tests = Files.createDirectories(temporaryDirectory.resolve("test"));
    final Path fixture = main.resolve("Broken.java");
    Files.writeString(fixture, "class Broken {\n  @SuppressWarnings(\"PMD.X\")\n}\n");

    assertThat(findSources(main, tests))
        .singleElement()
        .satisfies(
            violation -> {
              assertThat(violation.file()).isEqualTo(fixture);
              assertThat(violation.line()).isEqualTo(2);
              assertThat(violation.mechanism()).isEqualTo("SuppressWarnings");
            });
  }

  private static List<SourceViolation> findSources(final Path mainRoot, final Path testRoot)
      throws IOException {
    if (!Files.isDirectory(mainRoot)) {
      throw new IllegalStateException("Java source root is missing: " + mainRoot);
    }
    if (!Files.isDirectory(testRoot)) {
      throw new IllegalStateException("Java source root is missing: " + testRoot);
    }
    return Stream.concat(scanRoot(mainRoot), scanRoot(testRoot)).toList();
  }

  private static Stream<SourceViolation> scanRoot(final Path root) throws IOException {
    try (Stream<Path> paths = Files.walk(root)) {
      return paths
          .filter(path -> path.toString().endsWith(".java"))
          .flatMap(
              path ->
                  SuppressionSourceDetector.find(read(path)).stream()
                      .map(
                          diagnostic ->
                              new SourceViolation(path, diagnostic.line(), diagnostic.mechanism())))
          .toList()
          .stream();
    }
  }

  private static String read(final Path path) {
    try {
      return Files.readString(path);
    } catch (IOException exception) {
      throw new IllegalStateException("Could not read source file: " + path, exception);
    }
  }

  private record SourceViolation(Path file, int line, String mechanism) {}
}
