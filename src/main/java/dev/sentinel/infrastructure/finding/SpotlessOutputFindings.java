package dev.sentinel.infrastructure.finding;

import dev.sentinel.domain.gate.Finding;
import dev.sentinel.domain.gate.FindingSeverity;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Project;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;

/** Reads the list of unformatted files from Spotless check output; findings are file-level. */
public final class SpotlessOutputFindings implements GateFindingSource {
  static final String GATE = "format";
  private static final String HEADER = "The following files had format violations:";
  private static final Pattern LOG_PREFIX = Pattern.compile("^(\\[(ERROR|WARNING|INFO)\\] ?|> )");
  private static final Pattern FILE = Pattern.compile("^[^\\s@+\\-][^\\s]*\\.[A-Za-z0-9]+$");

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public SpotlessOutputFindings() {}

  @Override
  public String gateId() {
    return GATE;
  }

  @Override
  public List<Finding> extract(
      final Project project, final GateResult result, final Instant gateStart) {
    final Set<String> files = new LinkedHashSet<>();
    boolean inList = false;
    int fileIndent = -1;
    for (final String raw : OutputText.lines(result)) {
      if (raw.contains(HEADER)) {
        inList = true;
        fileIndent = -1;
        continue;
      }
      if (!inList) {
        continue;
      }
      final String line = LOG_PREFIX.matcher(raw).replaceFirst("");
      final String trimmed = line.strip();
      if (trimmed.isEmpty() || trimmed.startsWith("Run '")) {
        inList = false;
        continue;
      }
      final int indent = line.length() - line.stripLeading().length();
      if (FILE.matcher(trimmed).matches() && (fileIndent < 0 || indent == fileIndent)) {
        fileIndent = indent;
        files.add(ReportFiles.relativize(project, trimmed));
      }
    }
    final String message =
        "File is not formatted. Run '" + applyCommand(project) + "' to fix formatting.";
    return files.stream()
        .map(
            file ->
                new Finding(
                    GATE, "spotless", GATE, FindingSeverity.ERROR, message, file, null, null, null))
        .toList();
  }

  private static String applyCommand(final Project project) {
    return project.buildExecutable()
        + (project.buildTool() == BuildTool.GRADLE ? " spotlessApply" : " spotless:apply");
  }
}
