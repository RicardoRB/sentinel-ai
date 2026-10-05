package dev.sentinel.domain.agent;

import java.nio.file.Path;

public interface AgentIntegration extends AgentAdapter {
  IntegrationResult integrate(Path projectRoot, boolean remove);
}
