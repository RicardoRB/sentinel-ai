package dev.sentinel.application.spec;

import dev.sentinel.application.gate.CheckService;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.gate.SupportedQualityGates;
import dev.sentinel.domain.spec.OpenSpecOutput;
import dev.sentinel.domain.spec.SpecItemReport;
import dev.sentinel.domain.spec.SpecValidationReport;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Runs the spec validation gates configured in {@code sentinel.toml} and reports their findings.
 */
public class ConfiguredSpecValidation {
  private static final Logger LOGGER = Logger.getLogger(ConfiguredSpecValidation.class.getName());

  private final CheckService checks;

  /** Creates the validation; {@code checks} runs the configured OpenSpec gates. */
  @Inject
  public ConfiguredSpecValidation(final CheckService checks) {
    this.checks = checks;
  }

  /** Runs the enabled spec gates for the selected profiles from the detected project root. */
  public SpecValidationReport validate(final Path projectRoot, final List<String> profiles) {
    final CheckReport check = checks.check(projectRoot, profiles, SupportedQualityGates.specIds());
    LOGGER.log(Level.INFO, () -> "event=spec-validate-gates gates=" + check.results().size());
    return new SpecValidationReport(
        check.results().stream()
            .filter(result -> !result.passed() && result.status() != GateStatus.SKIPPED)
            .map(ConfiguredSpecValidation::gateReport)
            .toList());
  }

  private static SpecItemReport gateReport(final GateResult result) {
    final boolean silent = isBlank(result.stdout()) && isBlank(result.stderr());
    final String stderr = silent ? result.summary() : result.stderr();
    return new SpecItemReport(
        result.name() + " (" + result.status().name() + ")",
        OpenSpecOutput.parse(result.stdout(), stderr, false));
  }

  private static boolean isBlank(final String value) {
    return value == null || value.isBlank();
  }
}
