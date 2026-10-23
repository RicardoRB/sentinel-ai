package dev.ward.application.gate;

import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.RevisionFiles;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

/** Resolves the baseline commit of {@code openapi-diff} and {@code openapi-compat}. */
final class OpenApiBaselines {
  private static final String HEAD = "HEAD";

  private OpenApiBaselines() {}

  /** The last commit, for comparing uncommitted edits. */
  static Function<Path, OpenApiBaseline> lastCommit(final RevisionFiles revisions) {
    return root ->
        revisions
            .resolve(root, HEAD)
            .map(OpenApiBaseline::at)
            .orElseGet(
                () ->
                    OpenApiBaseline.stop(
                        GateStatus.PASSED,
                        "No baseline: the repository has no commits yet, so the spec is new.",
                        "no-head"));
  }

  /**
   * The merge base of {@code HEAD} and {@code base}, or the first parent of {@code HEAD} when
   * {@code HEAD} is that merge base.
   */
  static Function<Path, OpenApiBaseline> baseBranch(
      final RevisionFiles revisions, final String base) {
    return root -> {
      final Optional<String> head = revisions.resolve(root, HEAD);
      final Optional<String> mergeBase = revisions.mergeBase(root, base, HEAD);
      if (head.isEmpty() || mergeBase.isEmpty()) {
        return OpenApiBaseline.stop(
            GateStatus.FAILED,
            "No merge base between HEAD and "
                + base
                + ": fetch the base branch history (for example, a full-depth CI checkout).",
            "no-merge-base");
      }
      if (!mergeBase.get().equals(head.get())) {
        return OpenApiBaseline.at(mergeBase.get());
      }
      return revisions
          .resolve(root, head.get() + "^1")
          .map(OpenApiBaseline::at)
          .orElseGet(
              () ->
                  OpenApiBaseline.stop(
                      GateStatus.PASSED,
                      "No earlier revision to compare with: HEAD is the first commit.",
                      "no-parent"));
    };
  }
}
