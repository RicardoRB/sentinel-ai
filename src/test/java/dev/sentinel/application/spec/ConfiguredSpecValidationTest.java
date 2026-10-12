package dev.sentinel.application.spec;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.spec.SpecItemReport;
import dev.sentinel.domain.spec.SpecValidationReport;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfiguredSpecValidationTest extends SpecValidationTestSupport {

  @Test
  void configuredValidationRunsBothGatesAndReportsTheFailingOne() throws IOException {
    project(BOTH_ENABLED);

    final SpecValidationReport report = service().validateConfigured(root, List.of());

    assertThat(commands).containsExactly(ACTIVE_COMMAND, ARCHIVED_COMMAND);
    assertThat(report.items())
        .extracting(SpecItemReport::title)
        .containsExactly("openspec-archived (FAILED)");
    assertThat(report.items().getFirst().findings().getFirst().message())
        .isEqualTo("Change 'old' has incomplete tasks");
  }

  @Test
  void configuredValidationPassesSilentlyAndSkipsCodeQualityGates() throws IOException {
    project(
        """
        version = 1
        [quality-gates.tests]
        command = "mvn test"
        [quality-gates.openspec]
        command = ["openspec", "validate", "--all", "--strict", "--no-interactive"]
        """);

    final SpecValidationReport report = service().validateConfigured(root, List.of());

    assertThat(report.hasFindings()).isFalse();
    assertThat(commands).containsExactly(ACTIVE_COMMAND);
  }

  @Test
  void configuredValidationHonorsProfiles() throws IOException {
    project(
        """
        version = 1
        [quality-gates.tests]
        command = "mvn test"
        [quality-gates.openspec-archived]
        command = ["openspec", "validate", "--archived", "--no-interactive"]
        profiles = ["ci"]
        """);

    service().validateConfigured(root, List.of("ci"));

    assertThat(commands).containsExactly(ARCHIVED_COMMAND);
  }

  @Test
  void unavailableGateIsReportedWithItsSummary() throws IOException {
    project(BOTH_ENABLED);

    final SpecValidationReport report =
        service(inspection(false)).validateConfigured(root, List.of());

    assertThat(commands).isEmpty();
    assertThat(report.items())
        .extracting(SpecItemReport::title)
        .containsExactly("openspec (UNAVAILABLE)", "openspec-archived (UNAVAILABLE)");
    assertThat(report.items().getFirst().findings().getFirst().message())
        .contains("Install OpenSpec");
  }
}
