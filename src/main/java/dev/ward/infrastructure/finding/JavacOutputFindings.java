package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.project.Project;
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
      Pattern.compile(
          "^\\[(?<level>ERROR|WARNING)\\]\\s+(?<file>\\S.*?\\.java):\\[(?<line>\\d+),(?<column>\\d+)\\]\\s*(?<message>.*)$");
  // Gradle: /abs/Foo.java:12: error: cannot find symbol
  private static final Pattern GRADLE =
      Pattern.compile(
          "^(?<file>\\S.*?\\.java):(?<line>\\d+):\\s*(?<level>error|warning):\\s*(?<message>.*)$");

  /** Creates the stateless adapter that Dagger injects. */
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
                "ERROR".equals(maven.group("level")),
                new SourceLocation(maven.group("file"), maven.group("line"), maven.group("column")),
                maven.group("message")));
        continue;
      }
      final Matcher gradle = GRADLE.matcher(line);
      if (gradle.matches()) {
        findings.add(
            finding(
                project,
                "error".equals(gradle.group("level")),
                new SourceLocation(gradle.group("file"), gradle.group("line"), null),
                gradle.group("message")));
      }
    }
    return List.copyOf(findings);
  }

  private static Finding finding(
      final Project project,
      final boolean error,
      final SourceLocation location,
      final String message) {
    return new Finding(
        GATE,
        TOOL,
        error ? "javac.error" : "javac.warning",
        error ? FindingSeverity.ERROR : FindingSeverity.WARNING,
        message.isBlank() ? "Compilation problem." : message.strip(),
        ReportFiles.relativize(project, location.file()),
        positive(location.line()),
        positive(location.column()),
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

  /** A compiler-reported position; {@code line} and {@code column} are unparsed and may be null. */
  private record SourceLocation(String file, String line, String column) {}
}
