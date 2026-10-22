package dev.ward.domain.spec;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One reason the guard refuses an agent tool call.
 *
 * @param subject the refused path, skill name, or a short description of the input
 * @param delta the delta path to edit instead, or empty when none applies
 */
public record GuardRefusal(Reason reason, String subject, String delta) {

  /** The kind of protected action that was refused. */
  public enum Reason {
    APPROVED_SPEC,
    ARCHIVED_CHANGE,
    PROTECTED_ANCESTOR,
    ARCHIVE_COMMAND,
    ARCHIVE_OR_SYNC_SKILL,
    UNINSPECTABLE
  }

  /**
   * Validates the refusal.
   *
   * @throws NullPointerException if the reason, subject, or delta is missing
   */
  public GuardRefusal {
    Objects.requireNonNull(reason, "reason");
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(delta, "delta");
  }

  /** A refusal for a path inside, or containing, a protected area. */
  public static GuardRefusal forPath(final ProtectedArea area, final Path relative) {
    final String subject = relative.toString().isEmpty() ? "." : relative.toString();
    return switch (area) {
      case SPECS ->
          new GuardRefusal(
              Reason.APPROVED_SPEC, subject, ApprovedArtifactPolicy.deltaPath(relative));
      case ARCHIVE -> new GuardRefusal(Reason.ARCHIVED_CHANGE, subject, "");
      case ANCESTOR -> new GuardRefusal(Reason.PROTECTED_ANCESTOR, subject, "");
    };
  }

  /** Creates a refusal without a delta to edit instead. */
  public static GuardRefusal of(final Reason reason, final String subject) {
    return new GuardRefusal(reason, subject, "");
  }
}
