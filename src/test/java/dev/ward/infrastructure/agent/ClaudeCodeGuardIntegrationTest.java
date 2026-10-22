package dev.ward.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import dev.ward.domain.agent.GuardMode;
import dev.ward.domain.agent.IntegrationRequest;
import dev.ward.domain.agent.IntegrationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClaudeCodeGuardIntegrationTest {
  private static final IntegrationRequest ENABLE = new IntegrationRequest(false, GuardMode.ENABLE);
  private static final IntegrationRequest DISABLE =
      new IntegrationRequest(false, GuardMode.DISABLE);

  private final ClaudeCodeIntegration integration = new ClaudeCodeIntegration();

  @TempDir Path root;

  private Path settings() {
    return root.resolve(".claude/settings.json");
  }

  private Path guard() {
    return root.resolve(".claude/hooks/ward-openspec-guard");
  }

  private JsonTree preToolUse() throws IOException {
    return JsonTree.parse(Files.readString(settings())).get("hooks").get("PreToolUse");
  }

  @Test
  void enablingInstallsAnExecutableGuardAndRegistersIt() throws IOException {
    final IntegrationResult result = integration.integrate(root, ENABLE);

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(result.changed()).contains(guard().toString(), settings().toString());
    assertThat(Files.readString(guard()))
        .contains(
            ClaudeCodeIntegration.GUARD_MARKER,
            "exec ward hook claude-code --guard-approved-specs --event-stdin"
                + " -C \"${CLAUDE_PROJECT_DIR:-.}\"");
    assertThat(Files.isExecutable(guard())).isTrue();
    assertThat(preToolUse().get(0).get("matcher").asString())
        .isEqualTo("^(Edit|Write|MultiEdit|NotebookEdit|Bash|Skill)$");
    assertThat(preToolUse().get(0).get("hooks").get(0).get("command").asString())
        .isEqualTo("${CLAUDE_PROJECT_DIR}/.claude/hooks/ward-openspec-guard");
    final JsonTree hooks = JsonTree.parse(Files.readString(settings())).get("hooks");
    assertThat(hooks.has("PostToolUse")).isTrue();
    assertThat(hooks.has("Stop")).isTrue();
    assertThat(integration.integrate(root, ENABLE).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
  }

  @Test
  void refreshWithoutAnOptionKeepsAnInstalledGuard() throws IOException {
    integration.integrate(root, ENABLE);
    Files.writeString(guard(), "#!/bin/sh\n# " + ClaudeCodeIntegration.GUARD_MARKER + "\nold\n");

    final IntegrationResult result = integration.integrate(root, false);

    assertThat(result.changed()).containsExactly(guard().toString());
    assertThat(Files.readString(guard())).contains("--guard-approved-specs");
    assertThat(preToolUse().size()).isEqualTo(1);
  }

  @Test
  void refreshWithoutAnOptionLeavesTheGuardAbsentAndOutputUnchanged() throws IOException {
    integration.integrate(root, false);
    final String unguarded = Files.readString(settings());

    assertThat(integration.integrate(root, false).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
    assertThat(guard()).doesNotExist();
    assertThat(JsonTree.parse(unguarded).get("hooks").has("PreToolUse")).isFalse();

    integration.integrate(root, ENABLE);
    integration.integrate(root, DISABLE);
    assertThat(settings()).hasContent(unguarded);
  }

  @Test
  void disablingRemovesOnlyTheGuard() throws IOException {
    integration.integrate(root, ENABLE);

    final IntegrationResult result = integration.integrate(root, DISABLE);

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(result.changed())
        .containsExactlyInAnyOrder(guard().toString(), settings().toString());
    assertThat(guard()).doesNotExist();
    assertThat(root.resolve(".claude/hooks/ward-edit-write")).exists();
    assertThat(JsonTree.parse(Files.readString(settings())).get("hooks").has("PreToolUse"))
        .isFalse();
    assertThat(integration.integrate(root, DISABLE).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
  }

  @Test
  void removingTheIntegrationRemovesTheGuard() throws IOException {
    integration.integrate(root, ENABLE);

    final IntegrationResult result = integration.integrate(root, IntegrationRequest.REMOVE);

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(result.changed()).contains(guard().toString());
    assertThat(settings()).exists();
    assertThat(JsonTree.parse(Files.readString(settings())).has("wardOwnership")).isFalse();
    assertThat(root.resolve(".claude")).exists();
  }

  @Test
  void userOwnedGuardFileConflictsOnlyWhenTheGuardIsRequested() throws IOException {
    Files.createDirectories(guard().getParent());
    Files.writeString(guard(), "#!/bin/sh\necho mine\n");

    final IntegrationResult conflict = integration.integrate(root, ENABLE);
    assertThat(conflict.status()).isEqualTo(IntegrationResult.Status.CONFLICT);
    assertThat(conflict.changed()).containsExactly(guard().toString());
    assertThat(settings()).doesNotExist();

    assertThat(integration.integrate(root, false).status())
        .isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(JsonTree.parse(Files.readString(settings())).get("hooks").has("PreToolUse"))
        .isFalse();
    assertThat(integration.integrate(root, DISABLE).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
    assertThat(integration.integrate(root, IntegrationRequest.REMOVE).status())
        .isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(guard()).hasContent("#!/bin/sh\necho mine\n");
    assertThat(settings()).exists();
  }

  @Test
  void ownedGuardAloneIsStillFoundForRemoval() throws IOException {
    integration.integrate(root, ENABLE);
    Files.delete(settings());
    Files.delete(root.resolve(".claude/hooks/ward-edit-write"));

    assertThat(integration.integrate(root, IntegrationRequest.REMOVE).status())
        .isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(integration.integrate(root, IntegrationRequest.REMOVE).status())
        .isEqualTo(IntegrationResult.Status.NOT_FOUND);
  }
}
