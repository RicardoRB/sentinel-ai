package dev.ward.domain.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Renders spec findings as bounded plain text that fits agent hook context limits. */
public final class SpecReportRenderer {
  public static final int FINDINGS_PER_ITEM = 20;
  public static final int MAX_CHARACTERS = 8_000;

  private static final String INDENT = "  ";

  private SpecReportRenderer() {}

  /** Returns the rendered findings, or an empty string when there is nothing to report. */
  public static String render(final SpecValidationReport report) {
    final List<SpecItemReport> items =
        report.items().stream().filter(SpecItemReport::hasFindings).toList();
    final List<List<String>> blocks = items.stream().map(SpecReportRenderer::block).toList();
    final int total = items.stream().mapToInt(item -> item.findings().size()).sum();
    final List<String> lines = new ArrayList<>();
    int length = 0;
    int shown = 0;
    for (int index = 0; index < blocks.size(); index++) {
      final List<String> block = blocks.get(index);
      final int blockLength = block.stream().mapToInt(line -> line.length() + 1).sum();
      if (!lines.isEmpty() && length + blockLength > MAX_CHARACTERS) {
        lines.add("... " + (total - shown) + " more findings omitted (output limit reached)");
        return String.join("\n", lines);
      }
      lines.addAll(block);
      length += blockLength;
      shown += items.get(index).findings().size();
    }
    final String rendered = String.join("\n", lines);
    return rendered.length() > MAX_CHARACTERS
        ? rendered.substring(0, MAX_CHARACTERS) + "\n... output truncated"
        : rendered;
  }

  private static List<String> block(final SpecItemReport item) {
    final List<SpecFinding> findings = item.findings();
    final int visible = Math.min(findings.size(), FINDINGS_PER_ITEM);
    final List<String> block = new ArrayList<>();
    block.add(item.title() + ": " + counts(item));
    findings.subList(0, visible).forEach(finding -> block.add(line(finding)));
    if (findings.size() > visible) {
      block.add(INDENT + "... " + (findings.size() - visible) + " more findings omitted");
    }
    return block;
  }

  private static String line(final SpecFinding finding) {
    final String prefix =
        INDENT
            + finding.level().name()
            + " "
            + (finding.location() == null ? "" : finding.location() + ": ");
    return prefix + finding.message().replace("\n", "\n" + INDENT + INDENT);
  }

  private static String counts(final SpecItemReport item) {
    final List<String> parts = new ArrayList<>();
    for (final SpecFinding.Level level : SpecFinding.Level.values()) {
      final long count = item.count(level);
      if (count > 0) {
        final String name = level.name().toLowerCase(Locale.ROOT);
        parts.add(
            count + " " + (count == 1 || level == SpecFinding.Level.INFO ? name : name + "s"));
      }
    }
    return String.join(", ", parts);
  }
}
