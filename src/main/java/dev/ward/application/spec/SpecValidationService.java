package dev.ward.application.spec;

import dev.ward.application.gate.OpenSpecQualityGate;
import dev.ward.application.project.ProjectDetector;
import dev.ward.application.project.ProjectNotFoundException;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardConfigurationReader;
import dev.ward.domain.project.Project;
import dev.ward.domain.spec.OpenSpecLayout;
import dev.ward.domain.spec.ProjectPaths;
import dev.ward.domain.spec.SpecItem;
import dev.ward.domain.spec.SpecItemReport;
import dev.ward.domain.spec.SpecValidationReport;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/** Validates specification artifacts, either for edited paths or across configured spec gates. */
public class SpecValidationService {
  private static final Logger LOGGER = Logger.getLogger(SpecValidationService.class.getName());
  private static final Path CONFIGURATION = Path.of(WardConfiguration.FILE_NAME);

  private final ProjectDetector detector;
  private final WardConfigurationReader configurationReader;
  private final ProjectPaths paths;
  private final OpenSpecValidator validator;
  private final ConfiguredSpecValidation configured;

  /**
   * Creates the service. {@code validator} validates single items, and {@code configured} runs the
   * OpenSpec gates from the configuration.
   */
  @Inject
  public SpecValidationService(
      final ProjectDetector detector,
      final WardConfigurationReader configurationReader,
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
    final Optional<GateConfiguration> active = activeGate(project, profiles);
    if (active.isEmpty()) {
      LOGGER.log(Level.INFO, "event=spec-validate-skipped mode=targeted reason=not-enabled");
      return SpecValidationReport.EMPTY;
    }
    final Optional<Path> openSpecRoot = OpenSpecRoots.configured(project, active.get().root());
    if (openSpecRoot.isEmpty()) {
      LOGGER.log(Level.INFO, "event=spec-validate-skipped mode=targeted reason=root-outside");
      return SpecValidationReport.EMPTY;
    }
    final Path root = openSpecRoot.get();
    final List<Path> contained =
        editedPaths.stream()
            .map(path -> OpenSpecRoots.againstProject(project, root, path))
            .flatMap(Optional::stream)
            .map(path -> paths.containedRelative(root, path))
            .flatMap(Optional::stream)
            .toList();
    final List<SpecItem> items =
        OpenSpecLayout.route(contained).stream().filter(item -> exists(root, item)).toList();
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
        new SpecValidationReport(
            validator.validate(root, active.get().command().getFirst(), items));
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

  private Optional<GateConfiguration> activeGate(
      final Project project, final List<String> profiles) {
    if (!paths.isRegularFile(project.root(), CONFIGURATION)) {
      return Optional.empty();
    }
    final GateConfiguration active =
        configurationReader
            .read(project.root().resolve(CONFIGURATION))
            .forProfiles(profiles)
            .enabledGates()
            .get(OpenSpecQualityGate.ACTIVE);
    return active == null || active.command().isEmpty() ? Optional.empty() : Optional.of(active);
  }

  private boolean exists(final Path root, final SpecItem item) {
    final Path location = OpenSpecLayout.location(item);
    return item.kind() == SpecItem.Kind.CHANGE
        ? paths.isDirectory(root, location)
        : paths.isRegularFile(root, location);
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
                + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
  }
}
