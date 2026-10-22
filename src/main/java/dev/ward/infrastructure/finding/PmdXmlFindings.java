package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Reads PMD XML reports written by the Maven or Gradle PMD plugin. */
public final class PmdXmlFindings implements GateFindingSource {
  // PMD priorities run from 1 (high) to 5 (low); 3 is medium.
  private static final int MEDIUM_PRIORITY = 3;
  static final String GATE = "pmd";

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public PmdXmlFindings() {}

  @Override
  public String gateId() {
    return GATE;
  }

  @Override
  public List<Finding> extract(
      final Project project, final GateResult result, final Instant gateStart) {
    final List<Finding> findings = new ArrayList<>();
    for (final Path report :
        ReportFiles.freshReports(project, GATE, gateStart, "target/pmd.xml", "build/reports/pmd")) {
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
            case "violation" -> into.add(violation(reader, file[0]));
            default -> {
              // Processing errors and other elements carry no violation data.
            }
          }
        });
  }

  private static Finding violation(final XMLStreamReader reader, final String file)
      throws XMLStreamException {
    final String rule = reader.getAttributeValue(null, "rule");
    final String helpUri = reader.getAttributeValue(null, "externalInfoUrl");
    final Integer priority = SafeXml.intAttribute(reader, "priority");
    final Integer line = SafeXml.intAttribute(reader, "beginline");
    final Integer column = SafeXml.intAttribute(reader, "begincolumn");
    final String message = reader.getElementText().strip();
    return new Finding(
        GATE,
        GATE,
        rule == null || rule.isBlank() ? GATE : rule,
        severity(priority),
        message.isEmpty() ? "PMD violation." : message,
        file,
        line,
        column,
        helpUri == null || helpUri.isBlank() ? null : helpUri);
  }

  private static FindingSeverity severity(final Integer priority) {
    if (priority == null || priority < MEDIUM_PRIORITY) {
      return FindingSeverity.ERROR;
    }
    return priority == MEDIUM_PRIORITY ? FindingSeverity.WARNING : FindingSeverity.NOTE;
  }
}
