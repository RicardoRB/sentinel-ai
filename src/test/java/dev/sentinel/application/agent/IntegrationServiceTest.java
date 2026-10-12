package dev.sentinel.application.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.IntegrationRequest;
import dev.sentinel.domain.agent.IntegrationRequest.Guidance;
import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.agent.OpenSpecGuidanceLayout;
import dev.sentinel.domain.agent.OpenSpecSetup;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IntegrationServiceTest {
  private static final IntegrationRequest INSTALL_GUIDANCE =
      IntegrationRequest.install(Guidance.INCLUDED);

  @TempDir Path project;
  private final List<IntegrationRequest> adapterCalls = new ArrayList<>();
  private final List<OpenSpecGuidanceLayout> inspected = new ArrayList<>();
  private OpenSpecSetup setup = new OpenSpecSetup(true, true, true);
  private IntegrationResult adapterResult =
      new IntegrationResult(IntegrationResult.Status.CHANGED, List.of("guidance"), "done");

  @BeforeEach
  void createProject() throws IOException {
    Files.writeString(project.resolve("pom.xml"), "<project/>\n");
  }

  @Test
  void missingExecutableIsUnavailableAndWritesNothing() {
    setup = new OpenSpecSetup(false, true, true);

    final IntegrationResult result = service().integrate("claude-code", project, INSTALL_GUIDANCE);

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.UNAVAILABLE);
    assertThat(result.message())
        .contains("not on PATH", "executable configured only for Sentinel's gates")
        .contains("A human must install OpenSpec", "Sentinel did not install or run OpenSpec");
    assertThat(adapterCalls).isEmpty();
  }

  @Test
  void missingProjectDirectoryIsUnavailableAndWritesNothing() {
    setup = new OpenSpecSetup(true, false, true);

    final IntegrationResult result = service().integrate("opencode", project, INSTALL_GUIDANCE);

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.UNAVAILABLE);
    assertThat(result.message()).contains("A human must run `openspec init`", "did not run it");
    assertThat(adapterCalls).isEmpty();
  }

  @Test
  void missingVerifyWorkflowInstallsAndWarns() {
    setup = new OpenSpecSetup(true, true, false);

    final IntegrationResult result = service().integrate("claude-code", project, INSTALL_GUIDANCE);

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(result.warnings())
        .singleElement()
        .asString()
        .contains("`openspec config profile`", "`openspec update`", "Sentinel did not run");
    assertThat(adapterCalls).containsExactly(INSTALL_GUIDANCE);
    assertThat(inspected)
        .extracting(OpenSpecGuidanceLayout::agentId)
        .containsExactly("claude-code");
  }

  @Test
  void presentVerifyWorkflowInstallsWithoutWarning() {
    final IntegrationResult result = service().integrate("opencode", project, INSTALL_GUIDANCE);

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(result.warnings()).isEmpty();
  }

  @Test
  void conflictIsReturnedWithoutVerifyWarning() {
    setup = new OpenSpecSetup(true, true, false);
    adapterResult = new IntegrationResult(IntegrationResult.Status.CONFLICT, List.of("x"), "c");

    final IntegrationResult result = service().integrate("opencode", project, INSTALL_GUIDANCE);

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CONFLICT);
    assertThat(result.warnings()).isEmpty();
  }

  @Test
  void removalAndHookOnlyInstallSkipPrerequisites() {
    setup = new OpenSpecSetup(false, false, false);

    service().integrate("claude-code", project, IntegrationRequest.remove(Guidance.INCLUDED));
    service().integrate("claude-code", project, IntegrationRequest.remove(Guidance.ONLY));
    service().integrate("claude-code", project, IntegrationRequest.install(Guidance.EXCLUDED));

    assertThat(inspected).isEmpty();
    assertThat(adapterCalls).hasSize(3);
  }

  private IntegrationService service() {
    return new IntegrationService(
        new ProjectDetector(new FileSystemProjectInspection()),
        List.of(recording("claude-code"), recording("opencode")),
        (root, layout) -> {
          inspected.add(layout);
          return setup;
        });
  }

  private AgentIntegration recording(final String id) {
    return new AgentIntegration() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public IntegrationResult integrate(final Path root, final IntegrationRequest request) {
        adapterCalls.add(request);
        return adapterResult;
      }
    };
  }
}
