package dev.sentinel.infrastructure.learning;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.JsonTree;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.learning.LearningLedger;
import dev.sentinel.domain.learning.LearningRecord;
import dev.sentinel.domain.learning.LearningState;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LearningJsonContractFixtures {
  private static final Duration TESTS_DURATION = Duration.ofMillis(3847);
  private static final long CHECKSTYLE_FIRST_SEEN_SECONDS = 5;
  private static final long CHECKSTYLE_LAST_SEEN_SECONDS = 9;
  static final String CONTRACTS = "fixtures/learning-contracts/";
  static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");
  static final Project PROJECT =
      new Project(Path.of("/project"), Language.JAVA, BuildTool.MAVEN, Framework.SPRING_BOOT);

  private LearningJsonContractFixtures() {}

  static CheckReport report() {
    return new CheckReport(
        PROJECT,
        List.of(
            GateResult.builder("tests", GateStatus.PASSED)
                .command(List.of("./mvnw", "test"))
                .exitCode(0)
                .duration(TESTS_DURATION)
                .stdout("ok\n")
                .stderr("")
                .build()));
  }

  static LearningLedger ledger() {
    final Map<String, LearningRecord> records = new LinkedHashMap<>();
    records.put(
        "pmd:UnusedLocalVariable",
        new LearningRecord(
            "pmd:UnusedLocalVariable",
            "pmd",
            "UnusedLocalVariable",
            "Unicode ✓ and \"quotes\" — строка",
            2,
            LearningState.RESOLVED,
            TIME,
            TIME,
            true));
    records.put(
        "checkstyle:MagicNumber",
        new LearningRecord(
            "checkstyle:MagicNumber",
            "checkstyle",
            "MagicNumber",
            "escapes \n \t \\",
            1,
            LearningState.FAILING,
            TIME.plusSeconds(CHECKSTYLE_FIRST_SEEN_SECONDS),
            TIME.plusSeconds(CHECKSTYLE_LAST_SEEN_SECONDS),
            false));
    return new LearningLedger(LearningLedger.CURRENT_FORMAT_VERSION, records);
  }

  static String fixture(final String name) throws IOException {
    try (InputStream stream =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(CONTRACTS + name)) {
      assertThat(stream).as(CONTRACTS + name).isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
    }
  }

  static JsonTree recordsOf(final String storeJson) {
    return JsonTree.parse(storeJson).get("records");
  }
}
