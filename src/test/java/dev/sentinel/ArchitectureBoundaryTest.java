package dev.sentinel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import dev.sentinel.application.agent.fixture.PrivateImplementationCoupling;
import dev.sentinel.application.cyclealpha.Alpha;
import dev.sentinel.application.cyclebeta.Beta;
import dev.sentinel.application.gate.internal.HiddenImplementation;
import dev.sentinel.domain.architecturefixture.DirectFilesystemAccess;
import dev.sentinel.domain.architecturefixture.ExternalEffectAccess;
import dev.sentinel.domain.architecturefixture.FullyQualifiedAdapterReference;
import dev.sentinel.infrastructure.MisplacedAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(packages = "dev.sentinel", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureBoundaryTest {
  private static final Map<String, Set<String>> DOMAIN_FEATURE_EDGES =
      Map.of(
          "agent",
          Set.of(),
          "config",
          Set.of(),
          "doctor",
          Set.of(),
          "gate",
          Set.of("project", "process"),
          "init",
          Set.of("agent", "project"),
          "loop",
          Set.of("gate"),
          "policy",
          Set.of("gate"),
          "process",
          Set.of(),
          "project",
          Set.of());
  private static final Map<String, Set<String>> APPLICATION_ENTRY_POINTS =
      Map.of(
          "loop", Set.of("dev.sentinel.application.gate.CheckService"),
          "gate",
              Set.of(
                  "dev.sentinel.application.project.ProjectDetector",
                  "dev.sentinel.application.project.ProjectDiscovery",
                  "dev.sentinel.application.project.ProjectNotFoundException"),
          "init",
              Set.of(
                  "dev.sentinel.application.project.ProjectDetector",
                  "dev.sentinel.application.project.ProjectNotFoundException"),
          "agent",
              Set.of(
                  "dev.sentinel.application.project.ProjectDetector",
                  "dev.sentinel.application.project.ProjectNotFoundException"),
          "doctor", Set.of("dev.sentinel.application.project.ProjectDetector"),
          "project", Set.of());

  @ArchTest
  static void domainDoesNotDependOnOuterLayersOrExternalEffectImplementations(JavaClasses classes) {
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
                || target.startsWith("tools.jackson.")
                || target.startsWith("javax.xml.")
                || target.startsWith("org.w3c.dom."));
  }

  @ArchTest
  static void applicationDoesNotDependOnInfrastructureOrPerformExternalEffects(
      JavaClasses classes) {
    assertNoDependencies(
        classes,
        "dev.sentinel.application..",
        target ->
            target.startsWith("dev.sentinel.infrastructure.")
                || target.startsWith("dev.sentinel.config.")
                || target.startsWith("org.springframework.")
                || isInnerLayerExternalEffectApi(target)
                || target.startsWith("org.tomlj.")
                || target.startsWith("tools.jackson.")
                || target.startsWith("javax.xml.")
                || target.startsWith("org.w3c.dom."));
  }

  @ArchTest
  static void cliAdaptersDoNotDependOnOutboundAdaptersOrPerformFilesystemOrProcessWork(
      JavaClasses classes) {
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
  static void productionCodeDoesNotUseFieldInjection(JavaClasses classes) {
    ArchRule noFieldInjection =
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields()
            .should()
            .beAnnotatedWith(javax.inject.Inject.class);
    noFieldInjection.check(classes);
  }

  @ArchTest
  static void productionClassesFollowLayerFeaturePackagesAndExplicitRootExceptions(
      JavaClasses classes) {
    Set<String> commonCliTypes =
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
  static void springIsAbsentFromProductionDependencies(JavaClasses classes) {
    assertNoDependencies(
        classes, "dev.sentinel..", target -> target.startsWith("org.springframework."));
    assertThat(System.getProperty("java.class.path")).doesNotContain("spring");
  }

  @ArchTest
  static void domainAndApplicationFeatureGraphsAreAcyclicAndUseDocumentedEdges(
      JavaClasses production) {
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
    JavaClasses dependencies =
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
                    ArchitectureBoundaryTest::isInnerLayerExternalEffectApi))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertApplicationEntryPoints(dependencies))
        .isInstanceOf(AssertionError.class);

    assertThatThrownBy(
            () ->
                SlicesRuleDefinition.slices()
                    .matching("dev.sentinel.application.(*)..")
                    .should()
                    .beFreeOfCycles()
                    .check(new ClassFileImporter().importClasses(Alpha.class, Beta.class)))
        .isInstanceOf(AssertionError.class);

    Set<String> commonCliTypes =
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
    String pom = Files.readString(Path.of("pom.xml"));
    assertThat(pom).doesNotContain("spring-boot").doesNotContain("org.springframework");
  }

  @Test
  void archUnitImportsCompiledProjectClasses() {
    JavaClasses classes = new ClassFileImporter().importPackages("dev.sentinel.domain");

    assertThat(classes)
        .anyMatch(javaClass -> "dev.sentinel.domain.project.Project".equals(javaClass.getName()));
  }

  private static void assertDomainFeatureEdges(JavaClasses classes) {
    DOMAIN_FEATURE_EDGES.forEach(
        (sourceFeature, allowedTargets) ->
            assertNoDependencies(
                classes,
                "dev.sentinel.domain." + sourceFeature + "..",
                target -> {
                  if (!target.startsWith("dev.sentinel.domain.")) {
                    return false;
                  }
                  String[] parts = target.split("\\.");
                  return parts.length > 3
                      && !parts[3].equals(sourceFeature)
                      && !allowedTargets.contains(parts[3]);
                }));
  }

  private static void assertApplicationEntryPoints(JavaClasses classes) {
    APPLICATION_ENTRY_POINTS.forEach(
        (sourceFeature, allowedTargets) ->
            assertNoDependencies(
                classes,
                "dev.sentinel.application." + sourceFeature + "..",
                target -> {
                  if (!target.startsWith("dev.sentinel.application.")) {
                    return false;
                  }
                  String sourcePrefix = "dev.sentinel.application." + sourceFeature + ".";
                  return !target.startsWith(sourcePrefix) && !allowedTargets.contains(target);
                }));
  }

  private static void assertFeatureAllowlistCoverage(
      JavaClasses classes, String layerPrefix, Set<String> declaredFeatures) {
    Set<String> discoveredFeatures =
        classes.stream()
            .map(JavaClass::getPackageName)
            .filter(packageName -> packageName.startsWith(layerPrefix))
            .map(packageName -> packageName.substring(layerPrefix.length()).split("\\.")[0])
            .collect(Collectors.toSet());
    assertThat(declaredFeatures)
        .as("documented features for %s", layerPrefix)
        .containsExactlyInAnyOrderElementsOf(discoveredFeatures);
  }

  private static boolean isAllowedPackage(JavaClass javaClass, Set<String> commonCliTypes) {
    String name = javaClass.getName();
    String packageName = javaClass.getPackageName();
    return switch (packageName) {
      case "dev.sentinel" -> "dev.sentinel.SentinelApplication".equals(name);
      case "dev.sentinel.config" ->
          isDaggerGenerated(name)
              || Set.of(
                      "dev.sentinel.config.SentinelComponent",
                      "dev.sentinel.config.PortBindingsModule",
                      "dev.sentinel.config.CommandBindingsModule",
                      "dev.sentinel.config.SentinelProvidesModule")
                  .contains(name);
      case "dev.sentinel.infrastructure.cli" ->
          commonCliTypes.contains(javaClass.getSimpleName()) || isDaggerGenerated(name);
      default ->
          packageName.matches("dev\\.sentinel\\.(domain|application)\\.[^.]+(\\..*)?")
              || packageName.matches("dev\\.sentinel\\.infrastructure\\.[^.]+(\\..*)?");
    };
  }

  private static boolean isDaggerGenerated(String name) {
    return name.contains(".Dagger")
        || name.contains("Module_")
        || name.contains("_Factory")
        || name.contains("_") && name.endsWith("Factory")
        || name.contains("_MembersInjector")
        || name.contains("_Proxy");
  }

  private static boolean isInnerLayerExternalEffectApi(String target) {
    return target.startsWith("java.io.")
        || target.startsWith("java.net.")
        || target.startsWith("java.nio.channels.")
        || target.startsWith("java.nio.file.spi.")
        || Set.of(
                "java.lang.Process",
                "java.lang.ProcessBuilder",
                "java.lang.ProcessHandle",
                "java.lang.Runtime",
                "java.nio.file.Files",
                "java.nio.file.FileSystem",
                "java.nio.file.FileSystems",
                "java.nio.file.WatchService")
            .contains(target);
  }

  private static boolean isCliExternalEffectApi(String target) {
    return target.startsWith("java.net.")
        || target.startsWith("java.nio.channels.")
        || target.startsWith("java.nio.file.spi.")
        || Set.of(
                "java.lang.Process",
                "java.lang.ProcessBuilder",
                "java.lang.ProcessHandle",
                "java.lang.Runtime",
                "java.nio.file.Files",
                "java.nio.file.FileSystem",
                "java.nio.file.FileSystems",
                "java.nio.file.WatchService")
            .contains(target);
  }

  private static void assertNoDependencies(
      JavaClasses classes, String sourcePackage, Predicate<String> forbiddenTarget) {
    ArchRule rule =
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage(sourcePackage)
            .should()
            .dependOnClassesThat(
                new DescribedPredicate<JavaClass>("match forbidden dependency") {
                  @Override
                  public boolean test(JavaClass target) {
                    return forbiddenTarget.test(target.getName());
                  }
                });
    rule.check(classes);
  }
}
