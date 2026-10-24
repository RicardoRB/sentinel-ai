package dev.ward;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

final class ArchitectureBoundarySupport {
  // dev.ward.domain.<feature>: the feature is the fourth package segment.
  private static final int FEATURE_SEGMENT = 3;
  static final Map<String, Set<String>> DOMAIN_FEATURE_EDGES =
      Map.ofEntries(
          Map.entry("agent", Set.of()),
          Map.entry("config", Set.of()),
          Map.entry("doctor", Set.of()),
          Map.entry("gate", Set.of("project", "process")),
          Map.entry("init", Set.of("agent", "project")),
          Map.entry("loop", Set.of("gate")),
          Map.entry("policy", Set.of("gate")),
          Map.entry("provenance", Set.of()),
          Map.entry("process", Set.of()),
          Map.entry("terminal", Set.of()),
          Map.entry("project", Set.of()),
          Map.entry("json", Set.of()),
          Map.entry("learning", Set.of("gate")),
          Map.entry("spec", Set.of()),
          Map.entry("update", Set.of()),
          Map.entry("traceability", Set.of()));
  static final Map<String, Set<String>> APPLICATION_ENTRY_POINTS =
      Map.ofEntries(
          Map.entry("loop", Set.of("dev.ward.application.gate.CheckService")),
          Map.entry(
              "gate",
              Set.of(
                  "dev.ward.application.project.ProjectDetector",
                  "dev.ward.application.project.ProjectDiscovery",
                  "dev.ward.application.project.ProjectNotFoundException",
                  "dev.ward.application.traceability.TraceabilityPolicy",
                  "dev.ward.application.gate.OpenSpecJunitTraceabilityGate")),
          Map.entry(
              "init",
              Set.of(
                  "dev.ward.application.project.ProjectDetector",
                  "dev.ward.application.project.ProjectNotFoundException")),
          Map.entry(
              "agent",
              Set.of(
                  "dev.ward.application.project.ProjectDetector",
                  "dev.ward.application.project.ProjectNotFoundException")),
          Map.entry("doctor", Set.of("dev.ward.application.project.ProjectDetector")),
          Map.entry("project", Set.of()),
          Map.entry("learning", Set.of()),
          Map.entry(
              "spec",
              Set.of(
                  "dev.ward.application.gate.CheckService",
                  "dev.ward.application.gate.OpenSpecPrerequisites",
                  "dev.ward.application.gate.OpenSpecPrerequisites$Problem",
                  "dev.ward.application.gate.OpenSpecQualityGate",
                  "dev.ward.application.project.ProjectDetector",
                  "dev.ward.application.project.ProjectNotFoundException")),
          Map.entry(
              "provenance",
              Set.of(
                  "dev.ward.application.gate.CheckService",
                  "dev.ward.application.project.ProjectDetector",
                  "dev.ward.application.project.ProjectNotFoundException")),
          Map.entry("update", Set.of()),
          Map.entry("traceability", Set.of()));

  private ArchitectureBoundarySupport() {}

  static void assertDomainFeatureEdges(final JavaClasses classes) {
    DOMAIN_FEATURE_EDGES.forEach(
        (sourceFeature, allowedTargets) ->
            assertNoDependencies(
                classes,
                "dev.ward.domain." + sourceFeature + "..",
                target -> {
                  if (!target.startsWith("dev.ward.domain.")) {
                    return false;
                  }
                  final String[] parts = target.split("\\.");
                  return parts.length > FEATURE_SEGMENT
                      && !parts[FEATURE_SEGMENT].equals(sourceFeature)
                      && !allowedTargets.contains(parts[FEATURE_SEGMENT]);
                }));
  }

  static void assertApplicationEntryPoints(final JavaClasses classes) {
    APPLICATION_ENTRY_POINTS.forEach(
        (sourceFeature, allowedTargets) ->
            assertNoDependencies(
                classes,
                "dev.ward.application." + sourceFeature + "..",
                target -> {
                  if (!target.startsWith("dev.ward.application.")) {
                    return false;
                  }
                  final String sourcePrefix = "dev.ward.application." + sourceFeature + ".";
                  return !target.startsWith(sourcePrefix) && !allowedTargets.contains(target);
                }));
  }

  static void assertFeatureAllowlistCoverage(
      final JavaClasses classes, final String layerPrefix, final Set<String> declaredFeatures) {
    final Set<String> discoveredFeatures =
        classes.stream()
            .map(JavaClass::getPackageName)
            .filter(packageName -> packageName.startsWith(layerPrefix))
            .map(packageName -> packageName.substring(layerPrefix.length()).split("\\.")[0])
            .collect(Collectors.toSet());
    assertThat(declaredFeatures)
        .as("documented features for %s", layerPrefix)
        .containsExactlyInAnyOrderElementsOf(discoveredFeatures);
  }

  static boolean isAllowedPackage(final JavaClass javaClass, final Set<String> commonCliTypes) {
    final String name = javaClass.getName();
    final String packageName = javaClass.getPackageName();
    return switch (packageName) {
      case "dev.ward" -> "dev.ward.WardApplication".equals(name);
      case "dev.ward.config" ->
          isDaggerGenerated(name)
              || Set.of(
                      "dev.ward.config.WardComponent",
                      "dev.ward.config.PortBindingsModule",
                      "dev.ward.config.ProcessBindingsModule",
                      "dev.ward.config.ProjectBindingsModule",
                      "dev.ward.config.InitBindingsModule",
                      "dev.ward.config.BuildToolBindingsModule",
                      "dev.ward.config.CommandBindingsModule",
                      "dev.ward.config.SpecCommandBindingsModule",
                      "dev.ward.config.TraceabilityBindingsModule",
                      "dev.ward.config.ProvenanceBindingsModule",
                      "dev.ward.config.GateBindingsModule",
                      "dev.ward.config.WardProvidesModule")
                  .contains(name);
      case "dev.ward.infrastructure.cli" ->
          commonCliTypes.contains(javaClass.getSimpleName()) || isDaggerGenerated(name);
      default ->
          packageName.matches("dev\\.ward\\.(domain|application)\\.[^.]+(\\..*)?")
              || packageName.matches("dev\\.ward\\.infrastructure\\.[^.]+(\\..*)?");
    };
  }

  static boolean isDaggerGenerated(final String name) {
    return name.contains(".Dagger")
        || name.contains("Module_")
        || name.contains("_Factory")
        || name.contains("_") && name.endsWith("Factory")
        || name.contains("_MembersInjector")
        || name.contains("_Proxy");
  }

  static boolean isInnerLayerExternalEffectApi(final String target) {
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

  /**
   * APIs that define or load classes at run time. GraalVM native images contain only the classes
   * seen at build time, so these compile but fail when the executable runs.
   */
  static boolean isNativeImageIncompatibleApi(final String target) {
    return target.startsWith("java.lang.classfile.")
        || Set.of(
                "java.lang.invoke.MethodHandleProxies",
                "java.lang.invoke.MethodHandles$Lookup",
                "java.net.URLClassLoader",
                "java.security.SecureClassLoader")
            .contains(target);
  }

  static boolean isCliExternalEffectApi(final String target) {
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

  static void assertNoDependencies(
      final JavaClasses classes,
      final String sourcePackage,
      final Predicate<String> forbiddenTarget) {
    final ArchRule rule =
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage(sourcePackage)
            .should()
            .dependOnClassesThat(
                new DescribedPredicate<>("match forbidden dependency") {
                  @Override
                  public boolean test(final JavaClass target) {
                    return forbiddenTarget.test(target.getName());
                  }
                });
    rule.check(classes);
  }
}
