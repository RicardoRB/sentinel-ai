package dev.ward;

import static dev.ward.ArchitectureBoundarySupport.assertApplicationEntryPoints;
import static dev.ward.ArchitectureBoundarySupport.assertNoDependencies;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.ward.application.agent.fixture.PrivateImplementationCoupling;
import dev.ward.application.gate.internal.HiddenImplementation;
import dev.ward.domain.architecturefixture.DirectFilesystemAccess;
import dev.ward.domain.architecturefixture.ExternalEffectAccess;
import dev.ward.domain.architecturefixture.FullyQualifiedAdapterReference;
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
                    "dev.ward.domain.architecturefixture..",
                    target -> target.startsWith("dev.ward.infrastructure.")))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(
            () ->
                assertNoDependencies(
                    dependencies,
                    "dev.ward.domain.architecturefixture..",
                    ArchitectureBoundarySupport::isInnerLayerExternalEffectApi))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertApplicationEntryPoints(dependencies))
        .isInstanceOf(AssertionError.class);
  }
}
