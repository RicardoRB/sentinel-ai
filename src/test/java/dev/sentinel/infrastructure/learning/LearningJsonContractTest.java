package dev.sentinel.infrastructure.learning;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.JsonTree;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.learning.LearningLedger;
import dev.sentinel.domain.learning.LearningOutcome;
import dev.sentinel.domain.learning.LearningPrompt;
import dev.sentinel.domain.learning.LearningRecord;
import dev.sentinel.domain.learning.LearningState;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import dev.sentinel.infrastructure.cli.agent.ClaudeCodeHookCommand.HookOutput;
import dev.sentinel.infrastructure.cli.agent.ClaudeCodeHookCommand.HookSpecificOutput;
import dev.sentinel.infrastructure.cli.gate.JsonReportRenderer;
import dev.sentinel.infrastructure.json.ForyJsonCodec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Byte-for-byte contract verification of persistence, report, and hook JSON. */
class LearningJsonContractTest {
  private static final String CONTRACTS = "fixtures/learning-contracts/";
  private static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");
  private static final Project PROJECT =
      new Project(Path.of("/project"), Language.JAVA, BuildTool.MAVEN, Framework.SPRING_BOOT);

  @Test
  void generatedCodecClassesBackStoreReportAndHookDtos() throws Exception {
    assertThat(
            Class.forName(
                "dev.sentinel.infrastructure.learning.JsonLearningStore_d_FileDto_ForyJsonCodec"))
        .isNotNull();
    assertThat(
            Class.forName(
                "dev.sentinel.infrastructure.cli.gate.JsonReportRenderer_d_ReportDto_ForyJsonCodec"))
        .isNotNull();
    assertThat(
            Class.forName(
                "dev.sentinel.infrastructure.cli.agent.ClaudeCodeHookCommand_d_HookOutput_ForyJsonCodec"))
        .isNotNull();
  }

  @Test
  void storeSaveMatchesContractFixtureIncludingUnicodeAndTimestamps(@TempDir final Path root)
      throws Exception {
    final LearningLedger ledger = ledger();
    new JsonLearningStore(new ForyJsonCodec()).save(root, ledger);

    final String saved = Files.readString(root.resolve(".sentinel/learning.json"));
    // Record ordering is not contractual; content must match the fixture item for item.
    assertThat(recordsOf(saved)).isEqualTo(recordsOf(fixture("store-ledger.json")));
    assertThat(saved)
        .contains("Unicode ✓ and \\\"quotes\\\" — строка", "2026-01-01T00:00:00Z")
        .contains("escapes \\n \\t \\\\")
        .doesNotContain("stdout", "stderr", "command");
  }

  @Test
  void storeLoadsContractFixtureBackToSameValues() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-load");
    Files.createDirectories(root.resolve(".sentinel"));
    Files.writeString(root.resolve(".sentinel/learning.json"), fixture("store-ledger.json"));
    final LearningLedger loaded =
        new JsonLearningStore(new ForyJsonCodec()).load(root).orElseThrow();

    new JsonLearningStore(new ForyJsonCodec()).save(root, loaded);
    assertThat(recordsOf(Files.readString(root.resolve(".sentinel/learning.json"))))
        .isEqualTo(recordsOf(fixture("store-ledger.json")));

    final LearningRecord unicode = loaded.records().get("tests:FAILED");
    assertThat(unicode.summary()).isEqualTo("Unicode ✓ and \"quotes\" — строка");
    assertThat(unicode.occurrences()).isEqualTo(2);
    assertThat(unicode.state()).isEqualTo(LearningState.RESOLVED);
    assertThat(unicode.firstSeen()).isEqualTo(TIME);
    assertThat(unicode.lastSeen()).isEqualTo(TIME);
    assertThat(unicode.prompted()).isTrue();

    final LearningRecord escaped = loaded.records().get("checkstyle:ERROR");
    assertThat(escaped.summary()).isEqualTo("escapes \n \t \\");
    assertThat(escaped.state()).isEqualTo(LearningState.FAILING);
    assertThat(escaped.prompted()).isFalse();
  }

  @Test
  void emptyCollectionsRoundTrip(@TempDir final Path root) throws Exception {
    final JsonLearningStore store = new JsonLearningStore(new ForyJsonCodec());
    store.save(root, LearningLedger.empty());
    assertThat(Files.readString(root.resolve(".sentinel/learning.json")))
        .isEqualTo("{\"formatVersion\":1,\"records\":{}}");
    assertThat(store.load(root)).get().satisfies(ledger -> assertThat(ledger.records()).isEmpty());
  }

  @Test
  void reportWithLearningMatchesContractFixture() throws Exception {
    final LearningOutcome outcome =
        new LearningOutcome(
            LearningLedger.empty(),
            List.of(new LearningPrompt("tests:FAILED", "tests", 2, "BUILD FAILURE")),
            List.of());

    assertThat(
            new JsonReportRenderer(new ForyJsonCodec()).render(report(), false, false, outcome, 2))
        .isEqualTo(fixture("report-with-learning.json"));
  }

  @Test
  void reportWithoutLearningOmitsLearningObjectAndOptionalFields() throws Exception {
    final String json = new JsonReportRenderer(new ForyJsonCodec()).render(report());
    assertThat(json).isEqualTo(fixture("report-no-learning.json"));
    assertThat(json).doesNotContain("learning");
    assertThat(json).doesNotContain("errors").doesNotContain("warnings");
  }

  @Test
  void errorReportMatchesContractFixture() throws Exception {
    assertThat(new JsonReportRenderer(new ForyJsonCodec()).renderError("bad config"))
        .isEqualTo(fixture("report-error.json"));
  }

  @Test
  void hookOutputMatchesContractFixture() throws Exception {
    final HookOutput output = new HookOutput();
    output.hookSpecificOutput = new HookSpecificOutput();
    output.hookSpecificOutput.hookEventName = "PostToolUse";
    output.hookSpecificOutput.additionalContext =
        new LearningPrompt("tests:FAILED", "tests", 2, "BUILD FAILURE").instruction();

    assertThat(new ForyJsonCodec().toJson(output)).isEqualTo(fixture("hook-posttooluse.json"));
  }

  private static CheckReport report() {
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

  private static LearningLedger ledger() {
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

  private static String fixture(final String name) throws IOException {
    try (InputStream stream =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(CONTRACTS + name)) {
      assertThat(stream).as(CONTRACTS + name).isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
    }
  }

  private static JsonTree recordsOf(final String storeJson) {
    return JsonTree.parse(storeJson).get("records");
  }
}
