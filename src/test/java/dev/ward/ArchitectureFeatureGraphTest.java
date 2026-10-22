package dev.ward;

import static dev.ward.ArchitectureBoundarySupport.assertApplicationEntryPoints;
import static dev.ward.ArchitectureBoundarySupport.assertDomainFeatureEdges;
import static dev.ward.ArchitectureBoundarySupport.assertFeatureAllowlistCoverage;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import dev.ward.application.cyclealpha.Alpha;
import dev.ward.application.cyclebeta.CycleBeta;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(packages = "dev.ward", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureFeatureGraphTest {

  @ArchTest
  static void domainAndApplicationFeatureGraphsAreAcyclicAndUseDocumentedEdges(
      final JavaClasses production) {
    SlicesRuleDefinition.slices()
        .matching("dev.ward.domain.(*)..")
        .should()
        .beFreeOfCycles()
        .check(production);
    SlicesRuleDefinition.slices()
        .matching("dev.ward.application.(*)..")
        .should()
        .beFreeOfCycles()
        .check(production);
    assertFeatureAllowlistCoverage(
        production, "dev.ward.domain.", ArchitectureBoundarySupport.DOMAIN_FEATURE_EDGES.keySet());
    assertFeatureAllowlistCoverage(
        production,
        "dev.ward.application.",
        ArchitectureBoundarySupport.APPLICATION_ENTRY_POINTS.keySet());
    assertDomainFeatureEdges(production);
    assertApplicationEntryPoints(production);
  }

  @Test
  void sliceCycleRuleRejectsTheCycleFixture() {
    assertThatThrownBy(
            () ->
                SlicesRuleDefinition.slices()
                    .matching("dev.ward.application.(*)..")
                    .should()
                    .beFreeOfCycles()
                    .check(new ClassFileImporter().importClasses(Alpha.class, CycleBeta.class)))
        .isInstanceOf(AssertionError.class);
  }
}
