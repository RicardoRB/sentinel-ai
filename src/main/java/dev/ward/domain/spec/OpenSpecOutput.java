package dev.ward.domain.spec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts findings from OpenSpec validator output on both process streams. */
public final class OpenSpecOutput {
  static final int EXCERPT_LINES = 20;
  static final int EXCERPT_CHARACTERS = 2_000;

  private static final Pattern FINDING = Pattern.compile("\\[(ERROR|WARNING|INFO)\\]\\s*(.*)$");
  private static final Pattern LOCATED = Pattern.compile("^(\\S+):\\s+(.*)$");
  private static final String NEXT_STEPS = "Next steps:";

  private OpenSpecOutput() {}

  /**
   * Returns findings ordered by severity, then appearance. An unsuccessful run without recognizable
   * findings yields one ERROR holding a bounded excerpt of the combined output.
   */
  public static List<SpecFinding> parse(
      final String stdout, final String stderr, final boolean succeeded) {
    final List<String> lines = lines(stdout, stderr);
    final List<SpecFinding> findings = new ArrayList<>();
    for (final String line : lines) {
      final Matcher matcher = FINDING.matcher(line);
      if (matcher.find()) {
        findings.add(finding(SpecFinding.Level.valueOf(matcher.group(1)), matcher.group(2)));
      }
    }
    if (findings.isEmpty() && !succeeded) {
      findings.add(new SpecFinding(SpecFinding.Level.ERROR, null, excerpt(lines)));
    }
    findings.sort(Comparator.comparing(SpecFinding::level));
    return List.copyOf(findings);
  }

  private static SpecFinding finding(final SpecFinding.Level level, final String text) {
    final Matcher located = LOCATED.matcher(text.strip());
    return located.matches()
        ? new SpecFinding(level, located.group(1), located.group(2))
        : new SpecFinding(level, null, text.strip());
  }

  private static String excerpt(final List<String> lines) {
    final StringBuilder excerpt = new StringBuilder();
    int count = 0;
    for (final String line : lines) {
      if (line.strip().startsWith(NEXT_STEPS)) {
        break;
      }
      if (line.isBlank()) {
        continue;
      }
      if (count == EXCERPT_LINES) {
        break;
      }
      if (!excerpt.isEmpty()) {
        excerpt.append('\n');
      }
      excerpt.append(line.strip());
      count++;
    }
    if (excerpt.isEmpty()) {
      return "Validation failed without output.";
    }
    return excerpt.length() > EXCERPT_CHARACTERS
        ? excerpt.substring(0, EXCERPT_CHARACTERS) + "..."
        : excerpt.toString();
  }

  private static List<String> lines(final String stdout, final String stderr) {
    final List<String> lines = new ArrayList<>();
    for (final String stream : new String[] {stdout, stderr}) {
      if (stream != null && !stream.isEmpty()) {
        lines.addAll(stream.lines().toList());
      }
    }
    return lines;
  }
}
