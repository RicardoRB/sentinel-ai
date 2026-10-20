package dev.sentinel;

import static dev.sentinel.ArchitectureBoundarySupport.assertApplicationEntryPoints;
import static dev.sentinel.ArchitectureBoundarySupport.assertDomainFeatureEdges;
import static dev.sentinel.ArchitectureBoundarySupport.assertFeatureAllowlistCoverage;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import dev.sentinel.application.cyclealpha.Alpha;
import dev.sentinel.application.cyclebeta.CycleBeta;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(packages = "dev.sentinel", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureFeatureGraphTest {

  @ArchTest
  static void domainAndApplicationFeatureGraphsAreAcyclicAndUseDocumentedEdges(
      final JavaClasses production) {
    SlicesRuleDefinition.slices()
        .matching("dev.sentinel.domain.(*)..")
        .should()
        .beFreeOfCycles()
        .check(production);
    SlicesRuleDefinition.slices()
        .matching("dev.sentinel.application.(*)..")
        .should()
        .beFreeOfCycles()
        .check(production);
    assertFeatureAllowlistCoverage(
        production,
        "dev.sentinel.domain.",
        ArchitectureBoundarySupport.DOMAIN_FEATURE_EDGES.keySet());
    assertFeatureAllowlistCoverage(
        production,
        "dev.sentinel.application.",
        ArchitectureBoundarySupport.APPLICATION_ENTRY_POINTS.keySet());
    assertDomainFeatureEdges(production);
    assertApplicationEntryPoints(production);
  }

  @Test
  void sliceCycleRuleRejectsTheCycleFixture() {
    assertThatThrownBy(
            () ->
                SlicesRuleDefinition.slices()
                    .matching("dev.sentinel.application.(*)..")
                    .should()
                    .beFreeOfCycles()
                    .check(new ClassFileImporter().importClasses(Alpha.class, CycleBeta.class)))
        .isInstanceOf(AssertionError.class);
  }
}
