package dev.ward;

import static dev.ward.ArchitectureBoundarySupport.isAllowedPackage;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import dev.ward.infrastructure.MisplacedAdapter;
import java.util.Set;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(packages = "dev.ward", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitecturePlacementTest {
  private static final Set<String> COMMON_CLI_TYPES =
      Set.of(
          "CommandLineRunnerImpl",
          "ExitCodes",
          "DaggerCommandFactory",
          "ProjectOptions",
          "WardCommand",
          "VersionProvider");

  @ArchTest
  static void productionCodeDoesNotUseFieldInjection(final JavaClasses classes) {
    final ArchRule noFieldInjection =
        ArchRuleDefinition.noFields().should().beAnnotatedWith(javax.inject.Inject.class);
    noFieldInjection.check(classes);
  }

  @ArchTest
  static void productionClassesFollowLayerFeaturePackagesAndExplicitRootExceptions(
      final JavaClasses classes) {
    classes.forEach(
        javaClass ->
            assertThat(isAllowedPackage(javaClass, COMMON_CLI_TYPES))
                .as("package placement for %s", javaClass.getName())
                .isTrue());
  }

  @Test
  void placementRuleRejectsAMisplacedAdapterFixture() {
    assertThat(
            isAllowedPackage(
                new ClassFileImporter().importClasses(MisplacedAdapter.class).iterator().next(),
                COMMON_CLI_TYPES))
        .isFalse();
  }

  @Test
  void archUnitImportsCompiledProjectClasses() {
    final JavaClasses classes = new ClassFileImporter().importPackages("dev.ward.domain");

    assertThat(classes)
        .anyMatch(javaClass -> "dev.ward.domain.project.Project".equals(javaClass.getName()));
  }
}
