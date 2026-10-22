package dev.ward.infrastructure.agent;

import dev.ward.domain.agent.IntegrationResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Ownership-safe local pre-commit and GitHub Actions integrations for spec enforcement. */
public final class RepositorySpecIntegration {
  public static final String HOOK_MARKER = "# ward-owned: spec-enforce-pre-commit v1";
  public static final String DISPATCH_MARKER = "# ward-owned: spec-enforce-dispatch v1";
  public static final String WORKFLOW_MARKER = "# ward-owned: spec-enforce-workflow v1";
  private static final String DISPATCH =
      DISPATCH_MARKER
          + "\nset -eu\nbase=${WARD_BASE_REVISION:-HEAD^}\nexec ward spec enforce --base \"$base\"\n";
  private static final String HOOK =
      HOOK_MARKER
          + "\nset -eu\nexec \"$(git rev-parse --show-toplevel)/.ward/hooks/spec-enforce\"\n";
  private static final String WORKFLOW =
      WORKFLOW_MARKER
          + "\nname: Ward OpenSpec enforcement\n"
          + "on:\n  pull_request:\n\njobs:\n  spec-enforce:\n    name: Ward OpenSpec enforcement\n    runs-on: ubuntu-latest\n    steps:\n      - uses: actions/checkout@v5\n        with:\n          fetch-depth: 0\n          ref: ${{ github.event.pull_request.head.sha }}\n      - name: Fetch pull request base\n        run: git fetch --no-tags origin ${{ github.event.pull_request.base.ref }}\n      - name: Install tested OpenSpec\n        run: npm install --global @fission-ai/openspec@1.14.1\n      - name: Enforce OpenSpec provenance\n        run: ward spec enforce --base \"origin/${{ github.event.pull_request.base.ref }}\"\n\n# Enable this job as a required status check and required code-owner review in repository settings.\n# Git provenance is evidence of provenance, not proof of human approval.\n";

  /** Installs the Ward-owned pre-commit hook and its dispatch script, keeping a user-owned hook. */
  public IntegrationResult install(final Path projectRoot) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final Path dispatch = root.resolve(".ward/hooks/spec-enforce");
    final Path hook = root.resolve(".git/hooks/pre-commit");
    final Path workflow = root.resolve(".github/workflows/ward-spec-enforce.yml");
    try {
      final IntegrationResult conflict = preflight(hook, HOOK_MARKER, workflow, WORKFLOW_MARKER);
      if (conflict != null) {
        return conflict;
      }
      final IntegrationResult dispatchConflict =
          IntegrationArtifacts.preflight(dispatch, DISPATCH_MARKER);
      if (dispatchConflict != null) {
        return dispatchConflict;
      }
      final List<String> changed = new ArrayList<>();
      writeOwned(dispatch, DISPATCH, changed);
      if (!Files.exists(hook) || IntegrationArtifacts.owned(hook, HOOK_MARKER)) {
        writeOwned(hook, HOOK, changed);
      }
      writeOwned(workflow, WORKFLOW, changed);
      return changed.isEmpty()
          ? new IntegrationResult(
              IntegrationResult.Status.ALREADY_PRESENT,
              List.of(),
              "Ward repository integration is already present.")
          : new IntegrationResult(
              IntegrationResult.Status.CHANGED,
              changed,
              "Installed Ward-owned spec enforcement integration.");
    } catch (IOException e) {
      throw new UncheckedIOException("Could not update repository integration", e);
    }
  }

  /** Removes the Ward-owned hook, dispatch script, and workflow, leaving other files untouched. */
  public IntegrationResult remove(final Path projectRoot) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final List<Path> targets =
        List.of(
            root.resolve(".ward/hooks/spec-enforce"),
            root.resolve(".git/hooks/pre-commit"),
            root.resolve(".github/workflows/ward-spec-enforce.yml"));
    try {
      for (final Path target : targets) {
        if (Files.exists(target) && !IntegrationArtifacts.owned(target, marker(target))) {
          return new IntegrationResult(
              IntegrationResult.Status.CONFLICT,
              List.of(target.toString()),
              "User-owned repository integration preserved; resolve the conflict explicitly.");
        }
      }
      final List<String> removed = new ArrayList<>();
      for (final Path target : targets) {
        if (Files.deleteIfExists(target)) {
          removed.add(target.toString());
        }
      }
      return new IntegrationResult(
          IntegrationResult.Status.REMOVED,
          removed,
          "Removed Ward-owned spec enforcement integration.");
    } catch (IOException e) {
      throw new UncheckedIOException("Could not remove repository integration", e);
    }
  }

  /** Returns the GitHub Actions workflow that runs {@code ward spec enforce} on pull requests. */
  public static String workflowContent() {
    return WORKFLOW;
  }

  private static IntegrationResult preflight(
      final Path first, final String firstMarker, final Path second, final String secondMarker)
      throws IOException {
    final IntegrationResult firstResult = IntegrationArtifacts.preflight(first, firstMarker);
    if (firstResult != null) {
      return new IntegrationResult(
          IntegrationResult.Status.CONFLICT,
          firstResult.changed(),
          "Existing user-owned pre-commit hook preserved; manually chain Ward's .ward/hooks/spec-enforce if desired.");
    }
    return IntegrationArtifacts.preflight(second, secondMarker);
  }

  private static void writeOwned(
      final Path target, final String content, final List<String> changed) throws IOException {
    if (Files.exists(target) && content.equals(Files.readString(target))) {
      return;
    }
    Files.createDirectories(Objects.requireNonNull(target.toAbsolutePath().getParent()));
    Files.writeString(target, content);
    target.toFile().setExecutable(true, false);
    changed.add(target.toString());
  }

  private static String marker(final Path target) {
    return target.toString().endsWith("/pre-commit")
        ? HOOK_MARKER
        : target.toString().contains("workflow") ? WORKFLOW_MARKER : DISPATCH_MARKER;
  }
}
