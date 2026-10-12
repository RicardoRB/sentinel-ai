package dev.sentinel.application.agent;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.application.project.ProjectNotFoundException;
import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.IntegrationRequest;
import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.agent.OpenSpecGuidanceLayout;
import dev.sentinel.domain.agent.OpenSpecSetup;
import dev.sentinel.domain.agent.OpenSpecSetupInspection;
import dev.sentinel.domain.config.SentinelException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

public final class IntegrationService {
  private static final Logger LOGGER = Logger.getLogger(IntegrationService.class.getName());
  static final String MISSING_EXECUTABLE =
      "OpenSpec is not on PATH. Official OpenSpec agent workflows run `openspec` from PATH, so an"
          + " executable configured only for Sentinel's gates is not enough. A human must install"
          + " OpenSpec so that `openspec` is on PATH (for example `npm install -g"
          + " @fission-ai/openspec`). Sentinel did not install or run OpenSpec and wrote no files.";
  static final String MISSING_DIRECTORY =
      "The project has no openspec/ directory. A human must run `openspec init` in the project"
          + " root and select this agent's tools. Sentinel did not run it and wrote no files.";
  static final String MISSING_VERIFY =
      "The official OpenSpec verify workflow is not installed for this agent, so /opsx:verify is"
          + " unavailable. To enable it, a human must run `openspec config profile`, include the"
          + " verify workflow, then run `openspec update`. Sentinel did not run these commands.";

  private final ProjectDetector detector;
  private final List<AgentIntegration> integrations;
  private final OpenSpecSetupInspection openSpec;

  public IntegrationService(
      final ProjectDetector detector,
      final List<AgentIntegration> integrations,
      final OpenSpecSetupInspection openSpec) {
    this.detector = detector;
    this.integrations = List.copyOf(integrations);
    this.openSpec = openSpec;
  }

  @Inject
  public IntegrationService(
      final ProjectDetector detector,
      final Set<AgentIntegration> integrations,
      final OpenSpecSetupInspection openSpec) {
    this(detector, List.copyOf(integrations), openSpec);
  }

  public IntegrationResult integrate(
      final String agent, final Path start, final IntegrationRequest request) {
    LOGGER.log(
        Level.INFO,
        () ->
            "event=integration-start agent="
                + agent
                + " operation="
                + (request.removal() ? "remove" : "install")
                + " guidance="
                + request.guidance().name().toLowerCase(Locale.ROOT));
    final AgentIntegration adapter =
        integrations.stream()
            .filter(candidate -> candidate.id().equals(agent))
            .findFirst()
            .orElseThrow(
                () ->
                    new SentinelException(
                        "Unknown agent '" + agent + "'. Supported agents: opencode, claude-code"));
    final Path root = detector.detect(start).orElseThrow(ProjectNotFoundException::new).root();
    final IntegrationResult result =
        request.coversGuidance() && !request.removal()
            ? installWithGuidance(agent, adapter, root, request)
            : adapter.integrate(root, request);
    if (result.status() == IntegrationResult.Status.CONFLICT) {
      LOGGER.log(Level.WARNING, () -> "event=integration-conflict agent=" + agent);
    }
    LOGGER.log(
        Level.INFO,
        () ->
            "event=integration-complete agent="
                + agent
                + " status="
                + result.status().name().toLowerCase(Locale.ROOT)
                + " changed="
                + result.changed().size()
                + " warnings="
                + result.warnings().size());
    return result;
  }

  private IntegrationResult installWithGuidance(
      final String agent,
      final AgentIntegration adapter,
      final Path root,
      final IntegrationRequest request) {
    final OpenSpecGuidanceLayout layout =
        OpenSpecGuidanceLayout.forAgent(agent)
            .orElseThrow(
                () ->
                    new SentinelException(
                        "OpenSpec guidance is not available for agent '" + agent + "'."));
    final OpenSpecSetup setup = openSpec.inspect(root, layout);
    final String outcome = prerequisiteOutcome(setup);
    LOGGER.log(
        Level.INFO, () -> "event=openspec-prerequisite agent=" + agent + " outcome=" + outcome);
    if (!setup.executableOnPath()) {
      return unavailable(MISSING_EXECUTABLE);
    }
    if (!setup.projectDirectory()) {
      return unavailable(MISSING_DIRECTORY);
    }
    final IntegrationResult result = adapter.integrate(root, request);
    LOGGER.log(
        Level.INFO,
        () ->
            "event=openspec-verify-workflow agent=" + agent + " present=" + setup.verifyWorkflow());
    return setup.verifyWorkflow() || result.failed() ? result : result.withWarning(MISSING_VERIFY);
  }

  private static String prerequisiteOutcome(final OpenSpecSetup setup) {
    if (setup.executableOnPath() && setup.projectDirectory()) {
      return "ok";
    }
    return setup.executableOnPath() ? "missing-directory" : "missing-executable";
  }

  private static IntegrationResult unavailable(final String message) {
    return new IntegrationResult(IntegrationResult.Status.UNAVAILABLE, List.of(), message);
  }
}
