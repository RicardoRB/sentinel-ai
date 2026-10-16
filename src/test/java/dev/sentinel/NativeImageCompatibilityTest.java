package dev.sentinel;

import static dev.sentinel.ArchitectureBoundarySupport.assertNoDependencies;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import dev.sentinel.domain.architecturefixture.NativeImageIncompatibleAccess;
import org.junit.jupiter.api.Test;

/** Native images contain only build-time classes; JVM tests cannot see violations at run time. */
@AnalyzeClasses(packages = "dev.sentinel", importOptions = ImportOption.DoNotIncludeTests.class)
class NativeImageCompatibilityTest {

  @ArchTest
  static void productionCodeDoesNotDefineOrLoadClassesAtRunTime(final JavaClasses classes) {
    assertNoDependencies(
        classes, "dev.sentinel..", ArchitectureBoundarySupport::isNativeImageIncompatibleApi);
  }

  @Test
  void ruleRejectsRunTimeProxyClassGeneration() {
    assertThatThrownBy(
            () ->
                assertNoDependencies(
                    new ClassFileImporter().importClasses(NativeImageIncompatibleAccess.class),
                    "dev.sentinel.domain.architecturefixture..",
                    ArchitectureBoundarySupport::isNativeImageIncompatibleApi))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("MethodHandleProxies");
  }
}
