package dev.sentinel.domain.spec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Renders the refusal reason an agent receives, bounded to fit agent hook limits. */
public final class GuardFeedback {
  /** The maximum length of a rendered reason. */
  public static final int MAX_LENGTH = 4_000;

  private static final int MAX_CHANGES = 10;
  private static final int MAX_REFUSALS = 20;
  private static final String BULLET = "- ";
  private static final String TRUNCATED = "\n… output truncated";

  private GuardFeedback() {}

  /** Renders a reason naming each refusal, where to work instead, and who may archive. */
  public static String render(final List<GuardRefusal> refusals, final List<String> activeChanges) {
    final Set<GuardRefusal> distinct = new LinkedHashSet<>(refusals);
    final List<String> lines = new ArrayList<>();
    lines.add(
        "Sentinel blocked this tool call: approved OpenSpec artifacts are read-only for agents.");
    distinct.stream().limit(MAX_REFUSALS).map(GuardFeedback::describe).forEach(lines::add);
    if (distinct.size() > MAX_REFUSALS) {
      lines.add(BULLET + (distinct.size() - MAX_REFUSALS) + " more refused targets omitted");
    }
    lines.add(changes(activeChanges));
    lines.add(
        "Archiving a change or syncing deltas into openspec/specs/ requires a human. Ask the user to"
            + " run `openspec archive <change>` (or the sync workflow) themselves.");
    return bound(String.join("\n", lines));
  }

  private static String describe(final GuardRefusal refusal) {
    final String subject = refusal.subject();
    return BULLET
        + switch (refusal.reason()) {
          case APPROVED_SPEC ->
              subject
                  + " is an approved spec. Record the change as a delta in an active change: "
                  + refusal.delta();
          case ARCHIVED_CHANGE ->
              subject
                  + " is part of an archived change, which is read-only history. Create or update"
                  + " an active change under openspec/changes/<change>/ instead.";
          case PROTECTED_ANCESTOR ->
              subject
                  + " contains approved specs or archived changes; deleting or moving it would"
                  + " modify them.";
          case ARCHIVE_COMMAND ->
              "`openspec archive` archives a change and updates approved specs. Ask the user to"
                  + " run it themselves.";
          case ARCHIVE_OR_SYNC_SKILL ->
              "The skill "
                  + subject
                  + " archives or syncs into approved specs. Ask the user to run it themselves.";
          case UNINSPECTABLE ->
              "Sentinel could not inspect the "
                  + subject
                  + ", so it cannot confirm that approved artifacts stay unchanged. Use"
                  + " separate, simpler tool calls.";
        };
  }

  private static String changes(final List<String> activeChanges) {
    if (activeChanges.isEmpty()) {
      return "No active change exists. Create one with `openspec new change <name>`, then edit"
          + " its files under openspec/changes/<name>/.";
    }
    final String shown = String.join(", ", activeChanges.stream().limit(MAX_CHANGES).toList());
    final int hidden = activeChanges.size() - MAX_CHANGES;
    return "Active changes: " + shown + (hidden > 0 ? " (+" + hidden + " more)" : "");
  }

  private static String bound(final String text) {
    return text.length() <= MAX_LENGTH
        ? text
        : text.substring(0, MAX_LENGTH - TRUNCATED.length()) + TRUNCATED;
  }
}
