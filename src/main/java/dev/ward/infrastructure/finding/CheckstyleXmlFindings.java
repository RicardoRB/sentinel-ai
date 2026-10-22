package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;

/** Reads Checkstyle XML reports written by the Maven or Gradle Checkstyle plugin. */
public final class CheckstyleXmlFindings implements GateFindingSource {
  static final String GATE = "checkstyle";
  private static final String CHECKS_PACKAGE = "com.puppycrawl.tools.checkstyle.checks.";
  private static final String CHECK_SUFFIX = "Check";

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public CheckstyleXmlFindings() {}

  @Override
  public String gateId() {
    return GATE;
  }

  @Override
  public List<Finding> extract(
      final Project project, final GateResult result, final Instant gateStart) {
    final List<Finding> findings = new ArrayList<>();
    for (final Path report :
        ReportFiles.freshReports(
            project, GATE, gateStart, "target/checkstyle-result.xml", "build/reports/checkstyle")) {
      parse(project, report, findings);
    }
    return findings;
  }

  private static void parse(final Project project, final Path report, final List<Finding> into) {
    final String[] file = new String[1];
    SafeXml.read(
        report,
        reader -> {
          switch (reader.getLocalName()) {
            case "file" -> {
              file[0] = ReportFiles.relativize(project, reader.getAttributeValue(null, "name"));
            }
            case "error" -> {
              final String source = reader.getAttributeValue(null, "source");
              final String message = reader.getAttributeValue(null, "message");
              into.add(
                  new Finding(
                      GATE,
                      GATE,
                      ruleId(source),
                      severity(reader.getAttributeValue(null, "severity")),
                      message == null ? "Checkstyle violation." : message,
                      file[0],
                      SafeXml.intAttribute(reader, "line"),
                      SafeXml.intAttribute(reader, "column"),
                      helpUri(source)));
            }
            default -> {
              // Other elements carry no violation data.
            }
          }
        });
  }

  static String ruleId(final String source) {
    if (source == null || source.isBlank()) {
      return GATE;
    }
    final String simple = source.substring(source.lastIndexOf('.') + 1);
    return simple.endsWith(CHECK_SUFFIX) && simple.length() > CHECK_SUFFIX.length()
        ? simple.substring(0, simple.length() - CHECK_SUFFIX.length())
        : simple;
  }

  static String helpUri(final String source) {
    if (source == null || !source.startsWith(CHECKS_PACKAGE)) {
      return null;
    }
    final String relative = source.substring(CHECKS_PACKAGE.length());
    final int dot = relative.lastIndexOf('.');
    final String category = dot < 0 ? "misc" : relative.substring(0, dot).replace('.', '/');
    return "https://checkstyle.org/checks/"
        + category
        + "/"
        + ruleId(source).toLowerCase(Locale.ROOT)
        + ".html";
  }

  private static FindingSeverity severity(final String value) {
    if (value == null) {
      return FindingSeverity.ERROR;
    }
    return switch (value.toLowerCase(Locale.ROOT)) {
      case "warning" -> FindingSeverity.WARNING;
      case "info", "ignore" -> FindingSeverity.NOTE;
      default -> FindingSeverity.ERROR;
    };
  }
}
