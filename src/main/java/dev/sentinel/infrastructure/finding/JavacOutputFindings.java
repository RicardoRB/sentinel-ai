package dev.sentinel.infrastructure.finding;

import dev.sentinel.domain.gate.Finding;
import dev.sentinel.domain.gate.FindingSeverity;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.project.Project;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

/** Reads javac diagnostics from Maven or Gradle compile output. */
public final class JavacOutputFindings implements GateFindingSource {
  static final String GATE = "compile";
  private static final String TOOL = "javac";
  // Maven: [ERROR] /abs/Foo.java:[12,8] cannot find symbol
  private static final Pattern MAVEN =
      Pattern.compile("^\\[(ERROR|WARNING)\\]\\s+(\\S.*?\\.java):\\[(\\d+),(\\d+)\\]\\s*(.*)$");
  // Gradle: /abs/Foo.java:12: error: cannot find symbol
  private static final Pattern GRADLE =
      Pattern.compile("^(\\S.*?\\.java):(\\d+):\\s*(error|warning):\\s*(.*)$");

  @Inject
  public JavacOutputFindings() {}

  @Override
  public String gateId() {
    return GATE;
  }

  @Override
  public List<Finding> extract(
      final Project project, final GateResult result, final Instant gateStart) {
    // Maven repeats each error in its summary, so identical findings are kept once.
    final Set<Finding> findings = new LinkedHashSet<>();
    for (final String line : OutputText.lines(result)) {
      final Matcher maven = MAVEN.matcher(line);
      if (maven.matches()) {
        findings.add(
            finding(
                project,
                "ERROR".equals(maven.group(1)),
                maven.group(2),
                maven.group(3),
                maven.group(4),
                maven.group(5)));
        continue;
      }
      final Matcher gradle = GRADLE.matcher(line);
      if (gradle.matches()) {
        findings.add(
            finding(
                project,
                "error".equals(gradle.group(3)),
                gradle.group(1),
                gradle.group(2),
                null,
                gradle.group(4)));
      }
    }
    return List.copyOf(findings);
  }

  private static Finding finding(
      final Project project,
      final boolean error,
      final String file,
      final String line,
      final String column,
      final String message) {
    return new Finding(
        GATE,
        TOOL,
        error ? "javac.error" : "javac.warning",
        error ? FindingSeverity.ERROR : FindingSeverity.WARNING,
        message.isBlank() ? "Compilation problem." : message.strip(),
        ReportFiles.relativize(project, file),
        positive(line),
        positive(column),
        null);
  }

  private static Integer positive(final String value) {
    if (value == null) {
      return null;
    }
    try {
      final int parsed = Integer.parseInt(value);
      return parsed > 0 ? parsed : null;
    } catch (NumberFormatException exception) {
      return null;
    }
  }
}
