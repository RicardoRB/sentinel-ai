package dev.sentinel;

import static dev.sentinel.ArchitectureBoundarySupport.assertNoDependencies;
import static dev.sentinel.ArchitectureBoundarySupport.isCliExternalEffectApi;
import static dev.sentinel.ArchitectureBoundarySupport.isInnerLayerExternalEffectApi;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(packages = "dev.sentinel", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureBoundaryTest {

  @ArchTest
  static void domainDoesNotDependOnOuterLayersOrExternalEffectImplementations(
      final JavaClasses classes) {
    assertNoDependencies(
        classes,
        "dev.sentinel.domain..",
        target ->
            target.startsWith("dev.sentinel.application.")
                || target.startsWith("dev.sentinel.infrastructure.")
                || target.startsWith("dev.sentinel.config.")
                || target.startsWith("dagger.")
                || target.startsWith("javax.inject.")
                || target.startsWith("picocli.")
                || target.startsWith("org.springframework.")
                || isInnerLayerExternalEffectApi(target)
                || target.startsWith("org.tomlj.")
                || target.startsWith("org.apache.fory.")
                || target.startsWith("javax.xml.")
                || target.startsWith("org.w3c.dom."));
  }

  @ArchTest
  static void applicationDoesNotDependOnInfrastructureOrPerformExternalEffects(
      final JavaClasses classes) {
    assertNoDependencies(
        classes,
        "dev.sentinel.application..",
        target ->
            target.startsWith("dev.sentinel.infrastructure.")
                || target.startsWith("dev.sentinel.config.")
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
        "dev.sentinel.infrastructure.cli..",
        target ->
            (target.startsWith("dev.sentinel.infrastructure.")
                    && !target.startsWith("dev.sentinel.infrastructure.cli."))
                || isCliExternalEffectApi(target));
  }

  @ArchTest
  static void springIsAbsentFromProductionDependencies(final JavaClasses classes) {
    assertNoDependencies(
        classes, "dev.sentinel..", target -> target.startsWith("org.springframework."));
    assertThat(System.getProperty("java.class.path")).doesNotContain("spring");
  }

  @Test
  void pomDoesNotDeclareSpring() throws IOException {
    final String pom = Files.readString(Path.of("pom.xml"));
    assertThat(pom).doesNotContain("spring-boot").doesNotContain("org.springframework");
  }
}
