package dev.ward.infrastructure.agent;

import dev.ward.domain.agent.AgentIntegration;
import dev.ward.domain.agent.GuardMode;
import dev.ward.domain.agent.IntegrationCapability;
import dev.ward.domain.agent.IntegrationRequest;
import dev.ward.domain.agent.IntegrationResult;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.json.JsonCodec;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;

/** Ownership-safe Claude Code project hook integration. */
public final class ClaudeCodeIntegration implements AgentIntegration {
  public static final String SETTINGS_MARKER = "ward-owned: claude-code-edit-write v1";
  public static final String HOOK_MARKER = "ward-owned: edit-write-guard v1";
  public static final String GUARD_MARKER = "ward-owned: openspec-guard v1";
  private static final String SETTINGS_FILE = ".claude/settings.json";
  private static final String HOOK_FILE = ClaudeCodeFiles.HOOK_FILE;
  private static final String UPDATED = "Updated Ward Claude Code integration.";
  private final ClaudeSettingsDocument settingsDocument;

  private static final String SETTINGS =
      """
            {
              "wardOwnership": "%s",
              "hooks": {
                "PostToolUse": [
                  {
                    "matcher": "^(Edit|Write|MultiEdit)$",
                    "hooks": [
                      {
                        "type": "command",
                        "command": "${CLAUDE_PROJECT_DIR}/.claude/hooks/ward-edit-write",
                        "args": []
                      }
                    ]
                  }
                ],
                "Stop": [
                  {
                    "hooks": [
                      {
                        "type": "command",
                        "command": "${CLAUDE_PROJECT_DIR}/.claude/hooks/ward-edit-write",
                        "args": []
                      }
                    ]
                  }
                ]
              }
            }
            """
          .replace("%s", SETTINGS_MARKER);

  /** The settings with the opt-in guard registered before every guarded tool runs. */
  private static final String GUARDED_SETTINGS =
      SETTINGS.replace(
          "  \"hooks\": {\n",
          """
            "hooks": {
              "PreToolUse": [
                {
                  "matcher": "^(Edit|Write|MultiEdit|NotebookEdit|Bash|Skill)$",
                  "hooks": [
                    {
                      "type": "command",
                      "command": "${CLAUDE_PROJECT_DIR}/.claude/hooks/ward-openspec-guard",
                      "args": []
                    }
                  ]
                }
              ],
          """);

  private static final String GUARD =
      """
            #!/bin/sh
            # %s
            # Opt-in guard: Claude Code passes each PreToolUse event as JSON on stdin. Ward denies
            # tool calls that would modify approved OpenSpec specs or archived changes. Hooks run in
            # the session's current directory, so Ward is pointed at the project explicitly.
            exec ward hook claude-code --guard-approved-specs --event-stdin -C "${CLAUDE_PROJECT_DIR:-.}"
            """
          .replace("%s", GUARD_MARKER);

  private static final String HOOK =
      """
            #!/bin/sh
            # %s
            # Claude Code passes the hook event JSON on stdin. Ward reads it to validate edited
            # spec files after edits and all configured spec checks when the turn ends. Hooks run in
            # the session's current directory, so Ward is pointed at the project explicitly.
            exec ward hook claude-code --learn-after 3 --exclude-spec --event-stdin -C "${CLAUDE_PROJECT_DIR:-.}"
            """
          .replace("%s", HOOK_MARKER);

  /** Creates the integration with the default JSON codec. */
  public ClaudeCodeIntegration() {
    this(new ForyJsonCodec());
  }

  /** Creates the integration; {@code codec} reads and writes {@code .claude/settings.json}. */
  @Inject
  public ClaudeCodeIntegration(final JsonCodec codec) {
    settingsDocument = new ClaudeSettingsDocument(Objects.requireNonNull(codec));
  }

  @Override
  public String id() {
    return "claude-code";
  }

  @Override
  public Set<IntegrationCapability> capabilities() {
    return Set.of(
        IntegrationCapability.PRE_TOOL,
        IntegrationCapability.POST_EDIT,
        IntegrationCapability.TURN_END,
        IntegrationCapability.FEEDBACK,
        IntegrationCapability.APPROVED_ARTIFACT_GUARD);
  }

  @Override
  public boolean installed(final Path projectRoot) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    return Files.isRegularFile(root.resolve(SETTINGS_FILE))
        && Files.isRegularFile(root.resolve(HOOK_FILE));
  }

  @Override
  public IntegrationResult integrate(final Path projectRoot, final boolean remove) {
    return integrate(projectRoot, remove ? IntegrationRequest.REMOVE : IntegrationRequest.INSTALL);
  }

  @Override
  public boolean installsAtRepositoryRoot() {
    return true;
  }

  @Override
  public IntegrationResult integrate(final Path projectRoot, final IntegrationRequest request) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final ClaudeCodeFiles files = new ClaudeCodeFiles(request.projectPath());
    final Path settings = root.resolve(SETTINGS_FILE);
    final Path hook = root.resolve(files.hookFile());
    final GuardFile guard =
        new GuardFile(root.resolve(files.guardFile()), GUARD_MARKER, files.script(GUARD));
    try {
      return request.remove()
          ? remove(settings, hook, guard, files)
          : install(settings, hook, guard, request.guard(), files);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Could not update Claude Code integration: " + e.getMessage(), e);
    }
  }

  @Override
  public List<PlannedFileChange> plan(final Path projectRoot, final GuardMode mode) {
    return plan(projectRoot, new IntegrationRequest(false, mode));
  }

  /** Mirrors {@link #install}: the same preflight, settings merge, and guard order. */
  @Override
  public List<PlannedFileChange> plan(final Path projectRoot, final IntegrationRequest request) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final Path settings = root.resolve(SETTINGS_FILE);
    final ClaudeCodeFiles files;
    try {
      files = new ClaudeCodeFiles(request.projectPath());
    } catch (WardException unsafe) {
      return List.of(
          new PlannedFileChange(
              settings, PlannedFileChange.Action.CONFLICT, List.of(unsafe.getMessage())));
    }
    final GuardMode mode = request.guard();
    final GuardFile guard =
        new GuardFile(root.resolve(files.guardFile()), GUARD_MARKER, files.script(GUARD));
    final Path hookFile = root.resolve(files.hookFile());
    try {
      final PlannedFileChange hook =
          new PlannedFileChange(
              hookFile, IntegrationArtifacts.decide(hookFile, HOOK_MARKER, files.script(HOOK)));
      if (hook.action() == PlannedFileChange.Action.CONFLICT) {
        return List.of(hook);
      }
      if (guard.preflight(mode) != null) {
        return List.of(new PlannedFileChange(guard.file(), PlannedFileChange.Action.CONFLICT));
      }
      final String content;
      try {
        content =
            settingsDocument.merge(
                settings,
                SETTINGS_MARKER,
                files.settings(guard.enabled(mode) ? GUARDED_SETTINGS : SETTINGS),
                files.commands());
      } catch (ClaudeSettingsDocument.SettingsConflictException e) {
        return List.of(
            new PlannedFileChange(
                settings, PlannedFileChange.Action.CONFLICT, List.of(e.getMessage())));
      }
      final List<PlannedFileChange> plan = new ArrayList<>();
      guard.plan(mode).ifPresent(plan::add);
      plan.add(new PlannedFileChange(settings, ClaudeSettingsDocument.action(settings, content)));
      plan.add(hook);
      return plan;
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Could not plan Claude Code integration: " + e.getMessage(), e);
    }
  }

  /**
   * Installs or refreshes every file after a full conflict preflight. An enabled guard script is
   * written before the settings that reference it, and a disabled one is deleted after them.
   */
  private IntegrationResult install(
      final Path settings,
      final Path hook,
      final GuardFile guard,
      final GuardMode mode,
      final ClaudeCodeFiles files)
      throws IOException {
    final IntegrationResult hookConflict = IntegrationArtifacts.preflight(hook, HOOK_MARKER);
    if (hookConflict != null) {
      return hookConflict;
    }
    final IntegrationResult guardConflict = guard.preflight(mode);
    if (guardConflict != null) {
      return guardConflict;
    }
    final boolean guarded = guard.enabled(mode);
    final String settingsContent;
    try {
      settingsContent =
          settingsDocument.merge(
              settings,
              SETTINGS_MARKER,
              files.settings(guarded ? GUARDED_SETTINGS : SETTINGS),
              files.commands());
    } catch (ClaudeSettingsDocument.SettingsConflictException e) {
      return new IntegrationResult(
          IntegrationResult.Status.CONFLICT, List.of(settings.toString()), e.getMessage());
    }
    final List<Map.Entry<Path, IntegrationResult>> results = new ArrayList<>();
    if (guarded) {
      results.add(guard.install(UPDATED));
      IntegrationArtifacts.makeExecutable(guard.file());
    }
    results.add(
        Map.entry(settings, ClaudeSettingsDocument.write(settings, settingsContent, UPDATED)));
    results.add(
        Map.entry(
            hook,
            IntegrationArtifacts.installOrRefresh(hook, HOOK_MARKER, files.script(HOOK), UPDATED)));
    if (Files.exists(hook)) {
      IntegrationArtifacts.makeExecutable(hook);
    }
    guard.remove(mode == GuardMode.DISABLE).ifPresent(results::add);
    return IntegrationArtifacts.combine(
        results,
        "Ward Claude Code integration is already present.",
        "Created Ward-owned Claude Code edit/write integration (or refreshed it).");
  }

  private IntegrationResult remove(
      final Path settings, final Path hook, final GuardFile guard, final ClaudeCodeFiles files)
      throws IOException {
    final IntegrationResult conflict = IntegrationArtifacts.preflight(hook, HOOK_MARKER);
    if (conflict != null) {
      return conflict;
    }
    final ClaudeSettingsDocument.SettingsUpdate update;
    try {
      update = settingsDocument.remove(settings, SETTINGS_MARKER, files.commands());
    } catch (ClaudeSettingsDocument.SettingsConflictException e) {
      return new IntegrationResult(
          IntegrationResult.Status.CONFLICT, List.of(settings.toString()), e.getMessage());
    }
    if (update == null && !Files.exists(hook) && !guard.installed()) {
      return new IntegrationResult(
          IntegrationResult.Status.NOT_FOUND, List.of(), "No Ward Claude Code integration found.");
    }

    final List<String> removed = new ArrayList<>();
    if (update != null && update.changed()) {
      Files.writeString(settings, update.content());
      removed.add(settings.toString());
    }
    if (Files.exists(hook)) {
      Files.delete(hook);
      removed.add(hook.toString());
    }
    guard.remove(true).ifPresent(entry -> removed.add(entry.getKey().toString()));
    EmptyDirectories.delete(hook.getParent());
    EmptyDirectories.delete(settings.getParent());
    return new IntegrationResult(
        IntegrationResult.Status.REMOVED, removed, "Removed Ward-owned Claude Code integration.");
  }
}
