package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingExtractor;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.project.Project;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/** Routes a gate result to the finding source registered for its gate id. */
public final class CompositeFindingExtractor implements FindingExtractor {
  private static final Logger LOGGER = Logger.getLogger(CompositeFindingExtractor.class.getName());

  private final Map<String, GateFindingSource> sources;

  /** Creates the extractor that delegates to the extractor of each supported gate. */
  @Inject
  public CompositeFindingExtractor(
      final CheckstyleXmlFindings checkstyle,
      final PmdXmlFindings pmd,
      final GitleaksSarifFindings gitleaks,
      final JavacOutputFindings javac,
      final SpotlessOutputFindings spotless) {
    this(List.of(checkstyle, pmd, gitleaks, javac, spotless));
  }

  CompositeFindingExtractor(final List<GateFindingSource> sources) {
    final Map<String, GateFindingSource> byGate = new LinkedHashMap<>();
    sources.forEach(source -> byGate.put(source.gateId(), source));
    this.sources = Map.copyOf(byGate);
  }

  @Override
  public List<Finding> extract(
      final Project project, final GateResult result, final Instant gateStart) {
    final GateFindingSource source = sources.get(result.name());
    if (source == null) {
      LOGGER.log(Level.FINE, () -> "event=findings-unsupported gate=" + result.name());
      return List.of();
    }
    LOGGER.log(
        Level.INFO,
        () ->
            "event=findings-extract gate="
                + result.name()
                + " source="
                + source.getClass().getSimpleName()
                + " build-tool="
                + project.buildTool());
    return source.extract(project, result, gateStart);
  }
}
