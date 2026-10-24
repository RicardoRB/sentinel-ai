package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.QualityGateDefinitions;
import dev.ward.infrastructure.json.ForyJsonCodec;
import org.junit.jupiter.api.Test;

/** Every finding source reads the reports of a defined gate. */
class FindingSourceContractTest {
  @Test
  void everyFindingSourceNamesADefinedGate() {
    final CompositeFindingExtractor extractor =
        new CompositeFindingExtractor(
            new CheckstyleXmlFindings(),
            new PmdXmlFindings(),
            new GitleaksSarifFindings(new ForyJsonCodec()),
            new JavacOutputFindings(),
            new SpotlessOutputFindings());

    assertThat(extractor.gates())
        .allSatisfy(
            gate ->
                assertThat(QualityGateDefinitions.find(gate))
                    .as("finding source for undefined gate %s", gate)
                    .isPresent());
  }
}
