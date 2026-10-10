package dev.sentinel;

import static dev.sentinel.ArchitectureBoundarySupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import dev.sentinel.application.agent.fixture.PrivateImplementationCoupling;
import dev.sentinel.application.cyclealpha.Alpha;
import dev.sentinel.application.cyclebeta.CycleBeta;
import dev.sentinel.application.gate.internal.HiddenImplementation;
import dev.sentinel.domain.architecturefixture.DirectFilesystemAccess;
import dev.sentinel.domain.architecturefixture.ExternalEffectAccess;
import dev.sentinel.domain.architecturefixture.FullyQualifiedAdapterReference;
import dev.sentinel.infrastructure.MisplacedAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(packages = "dev.sentinel", importOptions = ImportOption.DoNotIncludeTests.class)
// This is one architectural specification with many independent rules.
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
            target.startsWith("dev.sentinel.infrastructure.agent.")
                || target.startsWith("dev.sentinel.infrastructure.config.")
                || target.startsWith("dev.sentinel.infrastructure.doctor.")
                || target.startsWith("dev.sentinel.infrastructure.init.")
                || target.startsWith("dev.sentinel.infrastructure.loop.")
                || target.startsWith("dev.sentinel.infrastructure.process.")
                || target.startsWith("dev.sentinel.infrastructure.project.")
                || isCliExternalEffectApi(target));
  }

  @ArchTest
  static void productionCodeDoesNotUseFieldInjection(final JavaClasses classes) {
    final ArchRule noFieldInjection =
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields()
            .should()
            .beAnnotatedWith(javax.inject.Inject.class);
    noFieldInjection.check(classes);
  }

  @ArchTest
  static void productionClassesFollowLayerFeaturePackagesAndExplicitRootExceptions(
      final JavaClasses classes) {
    final Set<String> commonCliTypes =
        Set.of(
            "CommandLineRunnerImpl",
            "ExitCodes",
            "DaggerCommandFactory",
            "ProjectOptions",
            "SentinelCommand",
            "VersionProvider");
    classes.forEach(
        javaClass ->
            assertThat(isAllowedPackage(javaClass, commonCliTypes))
                .as("package placement for %s", javaClass.getName())
                .isTrue());
  }

  @ArchTest
  static void springIsAbsentFromProductionDependencies(final JavaClasses classes) {
    assertNoDependencies(
        classes, "dev.sentinel..", target -> target.startsWith("org.springframework."));
    assertThat(System.getProperty("java.class.path")).doesNotContain("spring");
  }

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
        production, "dev.sentinel.domain.", DOMAIN_FEATURE_EDGES.keySet());
    assertFeatureAllowlistCoverage(
        production, "dev.sentinel.application.", APPLICATION_ENTRY_POINTS.keySet());
    assertDomainFeatureEdges(production);
    assertApplicationEntryPoints(production);
  }

  @Test
  void architectureRulesRejectForbiddenDependencyPlacementAndCycleFixtures() {
    final JavaClasses dependencies =
        new ClassFileImporter()
            .importClasses(
                FullyQualifiedAdapterReference.class,
                DirectFilesystemAccess.class,
                ExternalEffectAccess.class,
                PrivateImplementationCoupling.class,
                HiddenImplementation.class);
    assertThatThrownBy(
            () ->
                assertNoDependencies(
                    dependencies,
                    "dev.sentinel.domain.architecturefixture..",
                    target -> target.startsWith("dev.sentinel.infrastructure.")))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(
            () ->
                assertNoDependencies(
                    dependencies,
                    "dev.sentinel.domain.architecturefixture..",
                    ArchitectureBoundarySupport::isInnerLayerExternalEffectApi))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertApplicationEntryPoints(dependencies))
        .isInstanceOf(AssertionError.class);

    assertThatThrownBy(
            () ->
                SlicesRuleDefinition.slices()
                    .matching("dev.sentinel.application.(*)..")
                    .should()
                    .beFreeOfCycles()
                    .check(new ClassFileImporter().importClasses(Alpha.class, CycleBeta.class)))
        .isInstanceOf(AssertionError.class);

    final Set<String> commonCliTypes =
        Set.of(
            "CommandLineRunnerImpl",
            "ExitCodes",
            "DaggerCommandFactory",
            "ProjectOptions",
            "SentinelCommand",
            "VersionProvider");
    assertThat(
            isAllowedPackage(
                new ClassFileImporter().importClasses(MisplacedAdapter.class).iterator().next(),
                commonCliTypes))
        .isFalse();
  }

  @Test
  void pomDoesNotDeclareSpring() throws IOException {
    final String pom = Files.readString(Path.of("pom.xml"));
    assertThat(pom).doesNotContain("spring-boot").doesNotContain("org.springframework");
  }

  @Test
  void archUnitImportsCompiledProjectClasses() {
    final JavaClasses classes = new ClassFileImporter().importPackages("dev.sentinel.domain");

    assertThat(classes)
        .anyMatch(javaClass -> "dev.sentinel.domain.project.Project".equals(javaClass.getName()));
  }
}
