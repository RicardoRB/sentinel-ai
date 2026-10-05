package dev.sentinel.application.agent;

import com.google.inject.Inject;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.application.project.ProjectNotFoundException;
import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.config.SentinelException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class IntegrationService {
  private final ProjectDetector detector;
  private final List<AgentIntegration> integrations;

  public IntegrationService(ProjectDetector detector, List<AgentIntegration> integrations) {
    this.detector = detector;
    this.integrations = List.copyOf(integrations);
  }

  @Inject
  public IntegrationService(ProjectDetector detector, Set<AgentIntegration> integrations) {
    this(detector, List.copyOf(integrations));
  }

  public IntegrationResult integrate(String agent, Path start, boolean remove) {
    AgentIntegration adapter =
        integrations.stream()
            .filter(candidate -> candidate.id().equals(agent))
            .findFirst()
            .orElseThrow(
                () ->
                    new SentinelException(
                        "Unknown agent '" + agent + "'. Supported agents: opencode, claude-code"));
    Path root = detector.detect(start).orElseThrow(ProjectNotFoundException::new).root();
    return adapter.integrate(root, remove);
  }
}
