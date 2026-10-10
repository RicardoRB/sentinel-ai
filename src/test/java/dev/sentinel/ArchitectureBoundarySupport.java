package dev.sentinel;

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
  static final Map<String, Set<String>> DOMAIN_FEATURE_EDGES =
      Map.ofEntries(
          Map.entry("agent", Set.of()),
          Map.entry("config", Set.of()),
          Map.entry("doctor", Set.of()),
          Map.entry("gate", Set.of("project", "process")),
          Map.entry("init", Set.of("agent", "project")),
          Map.entry("loop", Set.of("gate")),
          Map.entry("policy", Set.of("gate")),
          Map.entry("process", Set.of()),
          Map.entry("terminal", Set.of()),
          Map.entry("project", Set.of()),
          Map.entry("json", Set.of()),
          Map.entry("learning", Set.of("gate")));
  static final Map<String, Set<String>> APPLICATION_ENTRY_POINTS =
      Map.ofEntries(
          Map.entry("loop", Set.of("dev.sentinel.application.gate.CheckService")),
          Map.entry(
              "gate",
              Set.of(
                  "dev.sentinel.application.project.ProjectDetector",
                  "dev.sentinel.application.project.ProjectDiscovery",
                  "dev.sentinel.application.project.ProjectNotFoundException")),
          Map.entry(
              "init",
              Set.of(
                  "dev.sentinel.application.project.ProjectDetector",
                  "dev.sentinel.application.project.ProjectNotFoundException")),
          Map.entry(
              "agent",
              Set.of(
                  "dev.sentinel.application.project.ProjectDetector",
                  "dev.sentinel.application.project.ProjectNotFoundException")),
          Map.entry("doctor", Set.of("dev.sentinel.application.project.ProjectDetector")),
          Map.entry("project", Set.of()),
          Map.entry("learning", Set.of()));

  private ArchitectureBoundarySupport() {}

  static void assertDomainFeatureEdges(final JavaClasses classes) {
    DOMAIN_FEATURE_EDGES.forEach(
        (sourceFeature, allowedTargets) ->
            assertNoDependencies(
                classes,
                "dev.sentinel.domain." + sourceFeature + "..",
                target -> {
                  if (!target.startsWith("dev.sentinel.domain.")) {
                    return false;
                  }
                  final String[] parts = target.split("\\.");
                  return parts.length > 3
                      && !parts[3].equals(sourceFeature)
                      && !allowedTargets.contains(parts[3]);
                }));
  }

  static void assertApplicationEntryPoints(final JavaClasses classes) {
    APPLICATION_ENTRY_POINTS.forEach(
        (sourceFeature, allowedTargets) ->
            assertNoDependencies(
                classes,
                "dev.sentinel.application." + sourceFeature + "..",
                target -> {
                  if (!target.startsWith("dev.sentinel.application.")) {
                    return false;
                  }
                  final String sourcePrefix = "dev.sentinel.application." + sourceFeature + ".";
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
      case "dev.sentinel" -> "dev.sentinel.SentinelApplication".equals(name);
      case "dev.sentinel.config" ->
          isDaggerGenerated(name)
              || Set.of(
                      "dev.sentinel.config.SentinelComponent",
                      "dev.sentinel.config.PortBindingsModule",
                      "dev.sentinel.config.ProcessBindingsModule",
                      "dev.sentinel.config.ProjectBindingsModule",
                      "dev.sentinel.config.InitBindingsModule",
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
