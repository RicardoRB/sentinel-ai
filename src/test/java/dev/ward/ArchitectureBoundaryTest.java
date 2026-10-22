package dev.ward;

import static dev.ward.ArchitectureBoundarySupport.assertNoDependencies;
import static dev.ward.ArchitectureBoundarySupport.isCliExternalEffectApi;
import static dev.ward.ArchitectureBoundarySupport.isInnerLayerExternalEffectApi;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(packages = "dev.ward", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureBoundaryTest {
  private static final List<String> DOMAIN_FORBIDDEN_PACKAGES =
      List.of(
          "dev.ward.application.",
          "dev.ward.infrastructure.",
          "dev.ward.config.",
          "dagger.",
          "javax.inject.",
          "picocli.",
          "org.springframework.",
          "org.tomlj.",
          "org.apache.fory.",
          "javax.xml.",
          "org.w3c.dom.");

  @ArchTest
  static void domainDoesNotDependOnOuterLayersOrExternalEffectImplementations(
      final JavaClasses classes) {
    assertNoDependencies(
        classes,
        "dev.ward.domain..",
        target ->
            DOMAIN_FORBIDDEN_PACKAGES.stream().anyMatch(target::startsWith)
                || isInnerLayerExternalEffectApi(target));
  }

  @ArchTest
  static void applicationDoesNotDependOnInfrastructureOrPerformExternalEffects(
      final JavaClasses classes) {
    assertNoDependencies(
        classes,
        "dev.ward.application..",
        target ->
            target.startsWith("dev.ward.infrastructure.")
                || target.startsWith("dev.ward.config.")
                || target.startsWith("org.springframework.")
                || isInnerLayerExternalEffectApi(target)
                || target.startsWith("org.tomlj.")
                || target.startsWith("org.apache.fory.")
                || target.startsWith("javax.xml.")
                || target.startsWith("org.w3c.dom."));
  }

  @ArchTest
  static void cliAdaptersDoNotDependOnOutboundAdaptersOrPerformFilesystemOrProcessWork(
      final JavaClasses classes) {
    assertNoDependencies(
        classes,
        "dev.ward.infrastructure.cli..",
        target ->
            (target.startsWith("dev.ward.infrastructure.")
                    && !target.startsWith("dev.ward.infrastructure.cli."))
                || isCliExternalEffectApi(target));
  }

  @ArchTest
  static void springIsAbsentFromProductionDependencies(final JavaClasses classes) {
    assertNoDependencies(
        classes, "dev.ward..", target -> target.startsWith("org.springframework."));
    assertThat(System.getProperty("java.class.path")).doesNotContain("spring");
  }

  @Test
  void pomDoesNotDeclareSpring() throws IOException {
    final String pom = Files.readString(Path.of("pom.xml"));
    assertThat(pom).doesNotContain("spring-boot").doesNotContain("org.springframework");
  }
}
