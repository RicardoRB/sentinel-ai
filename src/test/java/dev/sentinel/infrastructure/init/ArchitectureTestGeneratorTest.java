package dev.sentinel.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchitectureTestGeneratorTest {
  private static final QualityPreset.ArchitectureExtras ALL_EXTRAS =
      new QualityPreset.ArchitectureExtras(true, true, true);

  private final ArchitectureTestGenerator generator = new ArchitectureTestGenerator();

  @Test
  void analyzesThePackageSharedByEveryMainSource(@TempDir final Path root) throws IOException {
    // Created first, so a file walk that stops at the first source would pick this package.
    source(root, "com.acme.shop.application", "PlaceOrder");
    source(root, "com.acme.shop.domain", "Order");
    source(root, "com.acme.shop.infrastructure.web", "OrderController");

    final Path test = generator.apply(project(root), "hexagonal", ALL_EXTRAS).file();

    assertThat(test).isEqualTo(root.resolve("src/test/java/com/acme/shop/ArchitectureTest.java"));
    assertThat(Files.readString(test))
        .startsWith("package com.acme.shop;\n")
        .contains("packages = \"com.acme.shop\"", ".matching(\"com.acme.shop.(*)..\")");
  }

  @Test
  void aSingleLayerAnalyzesItsParentPackage(@TempDir final Path root) throws IOException {
    source(root, "com.acme.shop.domain", "Order");
    source(root, "com.acme.shop.domain.pricing", "Price");

    assertThat(generator.plan(project(root), "layered", ALL_EXTRAS).file())
        .isEqualTo(root.resolve("src/test/java/com/acme/shop/ArchitectureTest.java"));
  }

  @Test
  void projectsWithoutSourcesUseTheDefaultPackage(@TempDir final Path root) {
    assertThat(generator.plan(project(root), "layered", ALL_EXTRAS).file())
        .isEqualTo(root.resolve("src/test/java/com/example/ArchitectureTest.java"));
  }

  /** The expected text is google-java-format 1.30.0 output, so the format gate passes. */
  @Test
  void rendersSourceThatGoogleJavaFormatLeavesUnchanged() throws IOException {
    try (InputStream golden =
        getClass().getResourceAsStream("/init/hexagonal-strict-architecture-test.txt")) {
      assertThat(ArchitectureTestSource.render("com.acme.shop", "hexagonal", ALL_EXTRAS))
          .isEqualTo(new String(golden.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  @Test
  void wrapsTheAnnotationWhenThePackageIsLong() {
    final String source =
        ArchitectureTestSource.render(
            "com.example.lengthy.organization.department.product.modules",
            "clean",
            new QualityPreset.ArchitectureExtras(false, false, false));

    assertThat(source)
        .contains(
            "@AnalyzeClasses(\n"
                + "    packages = \"com.example.lengthy.organization.department.product.modules\",\n"
                + "    importOptions = ImportOption.DoNotIncludeTests.class)\n")
        .doesNotContain("SlicesRuleDefinition", "GeneralCodingRules");
  }

  private static void source(final Path root, final String packageName, final String type)
      throws IOException {
    final Path directory = root.resolve("src/main/java").resolve(packageName.replace('.', '/'));
    Files.createDirectories(directory);
    Files.writeString(
        directory.resolve(type + ".java"),
        "package " + packageName + ";\n\npublic class " + type + " {}\n");
  }

  private static Project project(final Path root) {
    return new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  }
}
