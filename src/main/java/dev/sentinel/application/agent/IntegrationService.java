package dev.sentinel.application.agent;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.application.project.ProjectNotFoundException;
import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.IntegrationRequest;
import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.agent.SupportedAgents;
import dev.sentinel.domain.config.SentinelException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/** Installs, refreshes, and removes agent integrations in the project containing a path. */
public final class IntegrationService {
  private static final Logger LOGGER = Logger.getLogger(IntegrationService.class.getName());
  private final ProjectDetector detector;
  private final List<AgentIntegration> integrations;

  /** Creates the service offering {@code integrations} in the given order. */
  public IntegrationService(
      final ProjectDetector detector, final List<AgentIntegration> integrations) {
    this.detector = detector;
    this.integrations = List.copyOf(integrations);
  }

  /** Creates the service offering every bound integration. */
  @Inject
  public IntegrationService(
      final ProjectDetector detector, final Set<AgentIntegration> integrations) {
    this(detector, List.copyOf(integrations));
  }

  /**
   * Installs or, when {@code remove}, removes the integration {@code agent}, keeping the guard
   * setting.
   */
  public IntegrationResult integrate(final String agent, final Path start, final boolean remove) {
    return integrate(agent, start, remove ? IntegrationRequest.REMOVE : IntegrationRequest.INSTALL);
  }

  /**
   * Applies {@code request} to the integration {@code agent} in the project containing {@code
   * start}.
   */
  public IntegrationResult integrate(
      final String agent, final Path start, final IntegrationRequest request) {
    LOGGER.log(
        Level.INFO,
        () ->
            "event=integration-start agent="
                + agent
                + " operation="
                + (request.remove() ? "remove" : "install")
                + " guard="
                + request.guard().name().toLowerCase(Locale.ROOT));
    final AgentIntegration adapter =
        integrations.stream()
            .filter(candidate -> candidate.id().equals(agent))
            .findFirst()
            .orElseThrow(
                () ->
                    new SentinelException(
                        "Unknown agent '"
                            + agent
                            + "'. Supported agents: "
                            + SupportedAgents.idsText()));
    final Path root = detector.detect(start).orElseThrow(ProjectNotFoundException::new).root();
    final IntegrationResult result =
        adapter.integrate(root, request).withCapabilities(adapter.capabilities());
    LOGGER.log(Level.INFO, () -> "event=integration-complete agent=" + agent + " status=completed");
    return result;
  }
}
