package dev.sentinel.application.agent;

import static dev.sentinel.ProjectFixtures.PLAIN_POM;
import static dev.sentinel.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.GuardMode;
import dev.sentinel.domain.agent.IntegrationRequest;
import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IntegrationServiceTest {
  private final List<Boolean> removals = new ArrayList<>();
  private final AgentIntegration legacy =
      new AgentIntegration() {
        @Override
        public String id() {
          return "legacy";
        }

        @Override
        public IntegrationResult integrate(final Path projectRoot, final boolean remove) {
          removals.add(remove);
          return new IntegrationResult(IntegrationResult.Status.CHANGED, List.of(), "ok");
        }
      };

  @Test
  void guardModeReachesTheIntegrationAndLegacyIntegrationsIgnoreIt(@TempDir final Path root) {
    withPom(root, PLAIN_POM);
    final List<IntegrationRequest> requests = new ArrayList<>();
    final AgentIntegration recording =
        new AgentIntegration() {
          @Override
          public String id() {
            return "recording";
          }

          @Override
          public IntegrationResult integrate(final Path projectRoot, final boolean remove) {
            return integrate(projectRoot, new IntegrationRequest(remove, GuardMode.KEEP));
          }

          @Override
          public IntegrationResult integrate(
              final Path projectRoot, final IntegrationRequest request) {
            requests.add(request);
            return new IntegrationResult(IntegrationResult.Status.CHANGED, List.of(), "ok");
          }
        };
    final IntegrationService service =
        new IntegrationService(
            new ProjectDetector(new FileSystemProjectInspection()), List.of(recording, legacy));

    service.integrate("recording", root, new IntegrationRequest(false, GuardMode.ENABLE));
    service.integrate("recording", root, true);
    service.integrate("legacy", root, new IntegrationRequest(false, GuardMode.DISABLE));

    assertThat(requests)
        .containsExactly(
            new IntegrationRequest(false, GuardMode.ENABLE), IntegrationRequest.REMOVE);
    assertThat(removals).containsExactly(false);
  }

  @Test
  void negatableOptionMapsToGuardModes() {
    assertThat(GuardMode.of(null)).isEqualTo(GuardMode.KEEP);
    assertThat(GuardMode.of(true)).isEqualTo(GuardMode.ENABLE);
    assertThat(GuardMode.of(false)).isEqualTo(GuardMode.DISABLE);
  }
}
