package dev.sentinel.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompositeFindingExtractorTest {
  @TempDir Path root;

  @Test
  void routesByGateIdAndIgnoresUnsupportedGates() {
    FindingFixtures.write(
        root.resolve("target/pmd.xml"),
        "<pmd><file name=\"A.java\"><violation beginline=\"1\" rule=\"R\" priority=\"1\">m"
            + "</violation></file></pmd>");
    final CompositeFindingExtractor extractor =
        new CompositeFindingExtractor(List.of(new CheckstyleXmlFindings(), new PmdXmlFindings()));

    assertThat(
            extractor.extract(
                FindingFixtures.maven(root),
                FindingFixtures.failed("pmd", ""),
                FindingFixtures.justBefore()))
        .singleElement()
        .satisfies(finding -> assertThat(finding.gateId()).isEqualTo("pmd"));
    assertThat(
            extractor.extract(
                FindingFixtures.maven(root),
                FindingFixtures.failed("tests", ""),
                FindingFixtures.justBefore()))
        .isEmpty();
    assertThat(
            extractor.extract(
                FindingFixtures.maven(root),
                FindingFixtures.failed("checkstyle", ""),
                FindingFixtures.justBefore()))
        .isEmpty();
  }
}
