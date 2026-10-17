package dev.sentinel.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.agent.GuardMode;
import dev.sentinel.domain.agent.IntegrationRequest;
import dev.sentinel.domain.agent.PlannedFileChange;
import dev.sentinel.domain.agent.PlannedFileChange.Action;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IntegrationPlanTest {
  private static final String SETTINGS = ".claude/settings.json";
  private static final String HOOK = ".claude/hooks/sentinel-edit-write";
  private static final String GUARD = ".claude/hooks/sentinel-openspec-guard";
  private static final String COMMAND = ".opencode/commands/sentinel-check.md";
  private static final String PLUGIN = ".opencode/plugins/sentinel-edit-write.js";

  private final ClaudeCodeIntegration claude = new ClaudeCodeIntegration();
  private final OpenCodeIntegration openCode = new OpenCodeIntegration();

  @Test
  void claudeInstallCreatesSettingsAndHookWithoutWriting(@TempDir final Path root) {
    assertThat(actions(root, claude.plan(root, GuardMode.KEEP)))
        .isEqualTo(Map.of(SETTINGS, Action.CREATE, HOOK, Action.CREATE));
    assertThat(root.resolve(".claude")).doesNotExist();
  }

  @Test
  void claudeGuardIsPlannedWhenEnabled(@TempDir final Path root) {
    assertThat(actions(root, claude.plan(root, GuardMode.ENABLE)))
        .isEqualTo(Map.of(GUARD, Action.CREATE, SETTINGS, Action.CREATE, HOOK, Action.CREATE));
  }

  @Test
  void disablingAnInstalledGuardRemovesIt(@TempDir final Path root) {
    claude.integrate(root, new IntegrationRequest(false, GuardMode.ENABLE));

    assertThat(actions(root, claude.plan(root, GuardMode.DISABLE)))
        .containsEntry(GUARD, Action.REMOVE)
        .containsEntry(SETTINGS, Action.MODIFY)
        .containsEntry(HOOK, Action.UNCHANGED);
    assertThat(root.resolve(GUARD)).exists();
  }

  @Test
  void invalidClaudeSettingsAreAConflict(@TempDir final Path root) throws IOException {
    Files.createDirectories(root.resolve(".claude"));
    Files.writeString(root.resolve(SETTINGS), "{ not json");

    assertThat(actions(root, claude.plan(root, GuardMode.KEEP)))
        .isEqualTo(Map.of(SETTINGS, Action.CONFLICT));
  }

  @Test
  void openCodeInstallCreatesCommandAndPlugin(@TempDir final Path root) {
    assertThat(actions(root, openCode.plan(root, GuardMode.KEEP)))
        .isEqualTo(Map.of(COMMAND, Action.CREATE, PLUGIN, Action.CREATE));
    assertThat(root.resolve(".opencode")).doesNotExist();
  }

  @Test
  void userOwnedOpenCodeCommandIsTheOnlyEntry(@TempDir final Path root) throws IOException {
    Files.createDirectories(root.resolve(COMMAND).getParent());
    Files.writeString(root.resolve(COMMAND), "user-owned\n");

    assertThat(actions(root, openCode.plan(root, GuardMode.KEEP)))
        .isEqualTo(Map.of(COMMAND, Action.CONFLICT));
  }

  private static Map<String, Action> actions(final Path root, final List<PlannedFileChange> plan) {
    return plan.stream()
        .collect(
            Collectors.toMap(
                change -> root.relativize(change.file()).toString(), PlannedFileChange::action));
  }
}
