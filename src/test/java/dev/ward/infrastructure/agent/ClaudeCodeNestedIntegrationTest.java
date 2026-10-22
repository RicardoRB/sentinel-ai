package dev.ward.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.agent.IntegrationRequest;
import dev.ward.domain.agent.IntegrationResult;
import dev.ward.domain.agent.PlannedFileChange;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Nested projects share the repository's Claude Code settings without touching other entries. */
class ClaudeCodeNestedIntegrationTest {
  private static final String SETTINGS = ".claude/settings.json";
  private static final String USER_SETTINGS =
      """
      {
        "hooks": {
          "PostToolUse": [
            {"matcher": "Edit", "hooks": [{"type": "command", "command": "./scripts/verify-edit.sh"}]}
          ]
        }
      }
      """;
  private static final String API = "apps/api";
  private static final String BILLING = "services/billing";
  private static final String API_HOOK =
      "${CLAUDE_PROJECT_DIR}/.claude/hooks/ward-edit-write-apps-api";
  private static final String BILLING_HOOK =
      "${CLAUDE_PROJECT_DIR}/.claude/hooks/ward-edit-write-services-billing";
  private static final String ROOT_HOOK = "${CLAUDE_PROJECT_DIR}/.claude/hooks/ward-edit-write";
  private static final String VERIFY = "./scripts/verify-edit.sh";

  @TempDir Path repository;

  private final ClaudeCodeIntegration integration = new ClaudeCodeIntegration();

  @Test
  void nestedProjectMergesIntoUserSettingsAndPointsWardAtTheProject() throws IOException {
    write(SETTINGS, USER_SETTINGS);

    final IntegrationResult result = integration.integrate(repository, install(API));

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(settings()).contains(VERIFY, API_HOOK).doesNotContain(ROOT_HOOK + "\"");
    assertThat(Files.readString(repository.resolve(".claude/hooks/ward-edit-write-apps-api")))
        .contains("cd \"$(dirname \"$0\")/../..\" || exit 2\nexec ward hook claude-code")
        .contains("-C 'apps/api'")
        .doesNotContain("CLAUDE_PROJECT_DIR:-.");
    assertThat(repository.resolve(".claude/hooks/ward-edit-write")).doesNotExist();
  }

  @Test
  void projectsInstallAndRemoveIndependently() throws IOException {
    write(SETTINGS, USER_SETTINGS);
    integration.integrate(repository, IntegrationRequest.INSTALL);
    integration.integrate(repository, install(API));
    integration.integrate(repository, install(BILLING));
    assertThat(settings()).contains(VERIFY, ROOT_HOOK, API_HOOK, BILLING_HOOK);

    assertThat(integration.integrate(repository, install(API)).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
    integration.integrate(repository, IntegrationRequest.REMOVE.forProject(API));

    assertThat(settings()).contains(VERIFY, ROOT_HOOK, BILLING_HOOK, "wardOwnership");
    assertThat(settings()).doesNotContain(API_HOOK);
    assertThat(repository.resolve(".claude/hooks/ward-edit-write-apps-api")).doesNotExist();
    assertThat(repository.resolve(".claude/hooks/ward-edit-write-services-billing")).exists();

    integration.integrate(repository, IntegrationRequest.REMOVE);
    integration.integrate(repository, IntegrationRequest.REMOVE.forProject(BILLING));

    assertThat(settings()).contains(VERIFY).doesNotContain("ward-edit-write", "wardOwnership");
  }

  @Test
  void nestedPlanListsTheSuffixedFiles() throws IOException {
    assertThat(integration.plan(repository, install(API)))
        .extracting(change -> repository.relativize(change.file()).toString())
        .containsExactly(SETTINGS, ".claude/hooks/ward-edit-write-apps-api");
    assertThat(integration.plan(repository, install("apps/it's")))
        .extracting(PlannedFileChange::action)
        .containsExactly(PlannedFileChange.Action.CONFLICT);
  }

  @Test
  void rootProjectFilesAreUnchanged() {
    final ClaudeCodeFiles root = new ClaudeCodeFiles("");

    assertThat(root.hookFile()).isEqualTo(".claude/hooks/ward-edit-write");
    assertThat(root.script("exec ward x -C \"${CLAUDE_PROJECT_DIR:-.}\""))
        .isEqualTo("exec ward x -C \"${CLAUDE_PROJECT_DIR:-.}\"");
    assertThat(root.settings("\"" + ROOT_HOOK + "\"")).isEqualTo("\"" + ROOT_HOOK + "\"");
  }

  private static IntegrationRequest install(final String path) {
    return IntegrationRequest.INSTALL.forProject(path);
  }

  private String settings() throws IOException {
    return Files.readString(repository.resolve(SETTINGS));
  }

  private void write(final String relative, final String content) throws IOException {
    final Path file = repository.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
