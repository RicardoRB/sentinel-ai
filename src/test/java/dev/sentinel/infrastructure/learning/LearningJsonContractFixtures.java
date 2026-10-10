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
  static final String CONTRACTS = "fixtures/learning-contracts/";
  static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");
  static final Project PROJECT =
      new Project(Path.of("/project"), Language.JAVA, BuildTool.MAVEN, Framework.SPRING_BOOT);

  private LearningJsonContractFixtures() {}

  static CheckReport report() {
    return new CheckReport(
        PROJECT,
        List.of(
            new GateResult(
                "tests",
                GateStatus.PASSED,
                List.of("./mvnw", "test"),
                0,
                Duration.ofMillis(3847),
                "ok\n",
                "")));
  }

  static LearningLedger ledger() {
    final Map<String, LearningRecord> records = new LinkedHashMap<>();
    records.put(
        "tests:FAILED",
        new LearningRecord(
            "tests:FAILED",
            "tests",
            "FAILED",
            "Unicode ✓ and \"quotes\" — строка",
            2,
            LearningState.RESOLVED,
            TIME,
            TIME,
            true));
    records.put(
        "checkstyle:ERROR",
        new LearningRecord(
            "checkstyle:ERROR",
            "checkstyle",
            "ERROR",
            "escapes \n \t \\",
            1,
            LearningState.FAILING,
            TIME.plusSeconds(5),
            TIME.plusSeconds(9),
            false));
    return new LearningLedger(1, records);
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
