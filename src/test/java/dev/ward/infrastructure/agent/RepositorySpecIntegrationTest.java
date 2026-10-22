package dev.ward.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.agent.IntegrationResult;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositorySpecIntegrationTest {
  @Test
  void workflowIsPinnedAndFetchesMergeBaseHistory() {
    final String workflow = RepositorySpecIntegration.workflowContent();

    assertThat(workflow)
        .contains("actions/checkout@v5", "fetch-depth: 0", "@fission-ai/openspec@1.14.1")
        .contains("git fetch", "ward spec enforce --base")
        .doesNotContain("/opsx:verify");
  }

  @Test
  void preservesUserOwnedHookWithoutPartialWrites(@TempDir final Path root) throws Exception {
    Files.createDirectories(root.resolve(".git/hooks"));
    final Path hook = root.resolve(".git/hooks/pre-commit");
    Files.writeString(hook, "#!/bin/sh\necho user\n");

    final IntegrationResult result = new RepositorySpecIntegration().install(root);

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CONFLICT);
    assertThat(Files.readString(hook)).contains("echo user");
    assertThat(root.resolve(".ward/hooks/spec-enforce")).doesNotExist();
  }

  @Test
  void refreshesAndRemovesOnlyOwnedArtifacts(@TempDir final Path root) throws Exception {
    Files.createDirectories(root.resolve(".git"));

    final RepositorySpecIntegration integration = new RepositorySpecIntegration();
    assertThat(integration.install(root).status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(integration.install(root).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
    assertThat(integration.remove(root).status()).isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(root.resolve(".github/workflows/ward-spec-enforce.yml")).doesNotExist();
  }
}
