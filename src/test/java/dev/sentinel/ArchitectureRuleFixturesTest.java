package dev.sentinel;

import static dev.sentinel.ArchitectureBoundarySupport.assertApplicationEntryPoints;
import static dev.sentinel.ArchitectureBoundarySupport.assertNoDependencies;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.sentinel.application.agent.fixture.PrivateImplementationCoupling;
import dev.sentinel.application.gate.internal.HiddenImplementation;
import dev.sentinel.domain.architecturefixture.DirectFilesystemAccess;
import dev.sentinel.domain.architecturefixture.ExternalEffectAccess;
import dev.sentinel.domain.architecturefixture.FullyQualifiedAdapterReference;
import org.junit.jupiter.api.Test;

class ArchitectureRuleFixturesTest {

  @Test
  void architectureRulesRejectForbiddenDependencyFixtures() {
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
  }
}
