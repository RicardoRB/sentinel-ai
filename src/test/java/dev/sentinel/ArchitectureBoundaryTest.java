package dev.sentinel;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import dev.sentinel.application.agent.fixture.PrivateImplementationCoupling;
import dev.sentinel.application.cyclealpha.Alpha;
import dev.sentinel.application.cyclebeta.Beta;
import dev.sentinel.application.gate.internal.HiddenImplementation;
import dev.sentinel.domain.architecturefixture.DirectFilesystemAccess;
import dev.sentinel.domain.architecturefixture.FullyQualifiedAdapterReference;
import dev.sentinel.infrastructure.MisplacedAdapter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchitectureBoundaryTest {
    private static final Map<String, Set<String>> DOMAIN_FEATURE_EDGES = Map.of(
            "agent", Set.of(), "config", Set.of(), "doctor", Set.of(),
            "gate", Set.of("project", "process"), "init", Set.of("agent", "project"),
            "loop", Set.of("gate"), "policy", Set.of("gate"), "process", Set.of(), "project", Set.of());
    private static final Map<String, Set<String>> APPLICATION_ENTRY_POINTS = Map.of(
            "loop", Set.of("dev.sentinel.application.gate.CheckService"),
            "gate", Set.of("dev.sentinel.application.project.ProjectDetector",
                    "dev.sentinel.application.project.ProjectDiscovery",
                    "dev.sentinel.application.project.ProjectNotFoundException"),
            "init", Set.of("dev.sentinel.application.project.ProjectDetector",
                    "dev.sentinel.application.project.ProjectNotFoundException",
                    "dev.sentinel.application.agent.IntegrationService"),
            "agent", Set.of("dev.sentinel.application.project.ProjectDetector",
                    "dev.sentinel.application.project.ProjectNotFoundException"),
            "doctor", Set.of("dev.sentinel.application.project.ProjectDetector"),
            "project", Set.of());

    @Test
    void domainDoesNotDependOnOuterLayersOrExternalEffectImplementations() {
        assertNoDependencies(classes(), "dev.sentinel.domain..", target ->
                target.startsWith("dev.sentinel.application.")
                        || target.startsWith("dev.sentinel.infrastructure.")
                        || target.startsWith("dev.sentinel.config.")
                        || target.startsWith("com.google.inject.")
                        || target.startsWith("picocli.")
                        || target.startsWith("org.springframework.")
                        || Set.of("java.lang.Process", "java.lang.ProcessBuilder", "java.nio.file.Files",
                        "java.nio.file.FileSystem").contains(target)
                        || target.startsWith("java.io.")
                        || target.startsWith("org.tomlj.")
                        || target.startsWith("tools.jackson.")
                        || target.startsWith("javax.xml.")
                        || target.startsWith("org.w3c.dom."));
    }

    @Test
    void applicationDoesNotDependOnInfrastructureOrPerformExternalEffects() {
        assertNoDependencies(classes(), "dev.sentinel.application..", target ->
                target.startsWith("dev.sentinel.infrastructure.")
                        || target.startsWith("dev.sentinel.config.")
                        || target.startsWith("org.springframework.")
                        || target.startsWith("java.io.")
                        || Set.of("java.lang.Process", "java.lang.ProcessBuilder", "java.lang.Runtime",
                        "java.nio.file.Files", "java.nio.file.FileSystem").contains(target)
                        || target.startsWith("org.tomlj.")
                        || target.startsWith("tools.jackson.")
                        || target.startsWith("javax.xml.")
                        || target.startsWith("org.w3c.dom."));
    }

    @Test
    void cliAdaptersDoNotDependOnOutboundAdaptersOrPerformFilesystemOrProcessWork() {
        assertNoDependencies(classes(), "dev.sentinel.infrastructure.cli..", target ->
                target.startsWith("dev.sentinel.infrastructure.agent.")
                        || target.startsWith("dev.sentinel.infrastructure.config.")
                        || target.startsWith("dev.sentinel.infrastructure.doctor.")
                        || target.startsWith("dev.sentinel.infrastructure.init.")
                        || target.startsWith("dev.sentinel.infrastructure.loop.")
                        || target.startsWith("dev.sentinel.infrastructure.process.")
                        || target.startsWith("dev.sentinel.infrastructure.project.")
                        || Set.of("java.lang.Process", "java.lang.ProcessBuilder", "java.nio.file.Files",
                        "java.nio.file.FileSystem").contains(target));
    }

    @Test
    void productionCodeDoesNotUseGuiceFieldInjection() {
        ArchRule noFieldInjection = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields()
                .should().beAnnotatedWith(com.google.inject.Inject.class);
        noFieldInjection.check(classes());
    }

    @Test
    void productionClassesFollowLayerFeaturePackagesAndExplicitRootExceptions() {
        Set<String> commonCliTypes = Set.of("CommandLineRunnerImpl", "ExitCodes", "GuiceCommandFactory",
                "ProjectOptions", "SentinelCommand", "VersionProvider");
        classes().forEach(javaClass -> assertThat(isAllowedPackage(javaClass, commonCliTypes))
                .as("package placement for %s", javaClass.getName()).isTrue());
    }

    @Test
    void springIsAbsentFromProductionDependenciesAndResolvedTestClasspath() {
        assertNoDependencies(classes(), "dev.sentinel..", target -> target.startsWith("org.springframework."));
        assertThat(System.getProperty("java.class.path")).doesNotContain("spring");
    }

    @Test
    void domainAndApplicationFeatureGraphsAreAcyclicAndUseDocumentedEdges() {
        JavaClasses production = classes();
        SlicesRuleDefinition.slices().matching("dev.sentinel.domain.(*)..")
                .should().beFreeOfCycles().check(production);
        SlicesRuleDefinition.slices().matching("dev.sentinel.application.(*)..")
                .should().beFreeOfCycles().check(production);
        assertDomainFeatureEdges(production);
        assertApplicationEntryPoints(production);
    }

    @Test
    void architectureRulesRejectForbiddenDependencyPlacementAndCycleFixtures() {
        JavaClasses dependencies = new ClassFileImporter().importClasses(
                FullyQualifiedAdapterReference.class, DirectFilesystemAccess.class,
                PrivateImplementationCoupling.class, HiddenImplementation.class);
        assertThatThrownBy(() -> assertNoDependencies(dependencies, "dev.sentinel.domain.architecturefixture..",
                target -> target.startsWith("dev.sentinel.infrastructure.")))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> assertNoDependencies(dependencies, "dev.sentinel.domain.architecturefixture..",
                target -> Set.of("java.nio.file.Files").contains(target)))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> assertApplicationEntryPoints(dependencies))
                .isInstanceOf(AssertionError.class);

        JavaClasses cycle = new ClassFileImporter().importClasses(Alpha.class, Beta.class);
        assertThatThrownBy(() -> SlicesRuleDefinition.slices().matching("dev.sentinel.application.(*)..")
                .should().beFreeOfCycles().check(cycle)).isInstanceOf(AssertionError.class);

        Set<String> commonCliTypes = Set.of("CommandLineRunnerImpl", "ExitCodes", "GuiceCommandFactory",
                "ProjectOptions", "SentinelCommand", "VersionProvider");
        JavaClasses misplaced = new ClassFileImporter().importClasses(MisplacedAdapter.class);
        assertThat(isAllowedPackage(misplaced.iterator().next(), commonCliTypes)).isFalse();
    }

    @Test
    void pomDoesNotDeclareSpring() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        assertThat(pom).doesNotContain("spring-boot").doesNotContain("org.springframework");
    }

    @Test
    void archUnitImportsCompiledProjectClasses() {
        JavaClasses classes = new ClassFileImporter().importPackages("dev.sentinel.domain");

        assertThat(classes).anyMatch(javaClass -> javaClass.getName().equals("dev.sentinel.domain.project.Project"));
    }

    private static JavaClasses classes() {
        return new ClassFileImporter().importPath(Path.of("target/classes"));
    }

    private static void assertDomainFeatureEdges(JavaClasses classes) {
        DOMAIN_FEATURE_EDGES.forEach((sourceFeature, allowedTargets) ->
                assertNoDependencies(classes, "dev.sentinel.domain." + sourceFeature + "..", target -> {
                    if (!target.startsWith("dev.sentinel.domain.")) return false;
                    String[] parts = target.split("\\.");
                    return parts.length > 3 && !parts[3].equals(sourceFeature)
                            && !allowedTargets.contains(parts[3]);
                }));
    }

    private static void assertApplicationEntryPoints(JavaClasses classes) {
        APPLICATION_ENTRY_POINTS.forEach((sourceFeature, allowedTargets) ->
                assertNoDependencies(classes, "dev.sentinel.application." + sourceFeature + "..", target -> {
                    if (!target.startsWith("dev.sentinel.application.")) return false;
                    String sourcePrefix = "dev.sentinel.application." + sourceFeature + ".";
                    return !target.startsWith(sourcePrefix) && !allowedTargets.contains(target);
                }));
    }

    private static boolean isAllowedPackage(JavaClass javaClass, Set<String> commonCliTypes) {
        String name = javaClass.getName();
        String packageName = javaClass.getPackageName();
        return switch (packageName) {
            case "dev.sentinel" -> name.equals("dev.sentinel.SentinelApplication");
            case "dev.sentinel.config" -> name.equals("dev.sentinel.config.SentinelModule");
            case "dev.sentinel.infrastructure.cli" -> commonCliTypes.contains(javaClass.getSimpleName());
            default -> packageName.matches("dev\\.sentinel\\.(domain|application)\\.[^.]+(\\..*)?")
                    || packageName.matches("dev\\.sentinel\\.infrastructure\\.[^.]+(\\..*)?");
        };
    }

    private static void assertNoDependencies(JavaClasses classes, String sourcePackage,
                                            java.util.function.Predicate<String> forbiddenTarget) {
        ArchRule rule = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
                .that().resideInAPackage(sourcePackage)
                .should().dependOnClassesThat(new DescribedPredicate<JavaClass>("match forbidden dependency") {
                    @Override
                    public boolean test(JavaClass target) {
                        return forbiddenTarget.test(target.getName());
                    }
                });
        rule.check(classes);
    }
}
