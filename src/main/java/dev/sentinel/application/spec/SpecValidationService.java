package dev.sentinel.application.spec;

import dev.sentinel.application.gate.OpenSpecQualityGate;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.application.project.ProjectNotFoundException;
import dev.sentinel.domain.config.GateConfiguration;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelConfigurationReader;
import dev.sentinel.domain.project.Project;
import dev.sentinel.domain.spec.OpenSpecLayout;
import dev.sentinel.domain.spec.ProjectPaths;
import dev.sentinel.domain.spec.SpecItem;
import dev.sentinel.domain.spec.SpecItemReport;
import dev.sentinel.domain.spec.SpecValidationReport;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/** Validates specification artifacts, either for edited paths or across configured spec gates. */
public class SpecValidationService {
  private static final Logger LOGGER = Logger.getLogger(SpecValidationService.class.getName());
  private static final Path CONFIGURATION = Path.of(SentinelConfiguration.FILE_NAME);

  private final ProjectDetector detector;
  private final SentinelConfigurationReader configurationReader;
  private final ProjectPaths paths;
  private final OpenSpecValidator validator;
  private final ConfiguredSpecValidation configured;

  @Inject
  public SpecValidationService(
      final ProjectDetector detector,
      final SentinelConfigurationReader configurationReader,
      final ProjectPaths paths,
      final OpenSpecValidator validator,
      final ConfiguredSpecValidation configured) {
    this.detector = detector;
    this.configurationReader = configurationReader;
    this.paths = paths;
    this.validator = validator;
    this.configured = configured;
  }

  /**
   * Validates the active spec items the given paths belong to. Nothing runs unless the active
   * OpenSpec validation is enabled in the selected profiles.
   */
  public SpecValidationReport validatePaths(
      final Path start, final List<String> profiles, final Collection<String> editedPaths) {
    final long started = System.nanoTime();
    final Project project = detect(start);
    final Optional<String> executable = activeExecutable(project, profiles);
    if (executable.isEmpty()) {
      LOGGER.log(Level.INFO, "event=spec-validate-skipped mode=targeted reason=not-enabled");
      return SpecValidationReport.EMPTY;
    }
    final List<Path> contained =
        editedPaths.stream()
            .map(path -> paths.containedRelative(project.root(), path))
            .flatMap(Optional::stream)
            .toList();
    final List<SpecItem> items =
        OpenSpecLayout.route(contained).stream().filter(item -> exists(project, item)).toList();
    LOGGER.log(
        Level.INFO,
        () ->
            "event=spec-validate-routed paths="
                + editedPaths.size()
                + " contained="
                + contained.size()
                + " changes="
                + count(items, SpecItem.Kind.CHANGE)
                + " specs="
                + count(items, SpecItem.Kind.SPEC));
    if (items.isEmpty()) {
      return SpecValidationReport.EMPTY;
    }
    final SpecValidationReport report =
        new SpecValidationReport(validator.validate(project.root(), executable.get(), items));
    log("targeted", report, started);
    return report;
  }

  /** Runs the enabled OpenSpec validation gates for the selected profiles. */
  public SpecValidationReport validateConfigured(final Path start, final List<String> profiles) {
    final long started = System.nanoTime();
    final Project project = detect(start);
    if (!paths.isRegularFile(project.root(), CONFIGURATION)) {
      LOGGER.log(Level.INFO, "event=spec-validate-skipped mode=configured reason=no-configuration");
      return SpecValidationReport.EMPTY;
    }
    final SpecValidationReport report = configured.validate(project.root(), profiles);
    log("configured", report, started);
    return report;
  }

  private Project detect(final Path start) {
    return detector.detect(start).orElseThrow(ProjectNotFoundException::new);
  }

  private Optional<String> activeExecutable(final Project project, final List<String> profiles) {
    if (!paths.isRegularFile(project.root(), CONFIGURATION)) {
      return Optional.empty();
    }
    final GateConfiguration active =
        configurationReader
            .read(project.root().resolve(CONFIGURATION))
            .forProfiles(profiles)
            .enabledGates()
            .get(OpenSpecQualityGate.ACTIVE);
    return active == null || active.command().isEmpty()
        ? Optional.empty()
        : Optional.of(active.command().getFirst());
  }

  private boolean exists(final Project project, final SpecItem item) {
    final Path location = OpenSpecLayout.location(item);
    return item.kind() == SpecItem.Kind.CHANGE
        ? paths.isDirectory(project.root(), location)
        : paths.isRegularFile(project.root(), location);
  }

  private static long count(final List<SpecItem> items, final SpecItem.Kind kind) {
    return items.stream().filter(item -> item.kind() == kind).count();
  }

  private static void log(
      final String mode, final SpecValidationReport report, final long started) {
    LOGGER.log(
        Level.INFO,
        () ->
            "event=spec-validate-complete mode="
                + mode
                + " items="
                + report.items().size()
                + " with-findings="
                + report.items().stream().filter(SpecItemReport::hasFindings).count()
                + " durationMs="
                + (System.nanoTime() - started) / 1_000_000);
  }
}
