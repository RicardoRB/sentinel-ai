package dev.sentinel.infrastructure.learning;

import static dev.sentinel.infrastructure.learning.LearningJsonContractFixtures.fixture;
import static dev.sentinel.infrastructure.learning.LearningJsonContractFixtures.report;
import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.learning.LearningLedger;
import dev.sentinel.domain.learning.LearningOutcome;
import dev.sentinel.domain.learning.LearningPrompt;
import dev.sentinel.infrastructure.cli.agent.ClaudeCodeHookCommand.HookOutput;
import dev.sentinel.infrastructure.cli.agent.ClaudeCodeHookCommand.HookSpecificOutput;
import dev.sentinel.infrastructure.cli.gate.JsonReportRenderer;
import dev.sentinel.infrastructure.json.ForyJsonCodec;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Byte-for-byte contract verification of codec, report, and hook JSON. */
class LearningJsonContractTest {
  private static final String EXAMPLE = "Avoid unused local variables such as 'x'.";

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
  void reportWithLearningMatchesContractFixture() throws Exception {
    final LearningOutcome outcome =
        new LearningOutcome(
            LearningLedger.empty(),
            List.of(
                new LearningPrompt(
                    "pmd:UnusedLocalVariable", "pmd", "UnusedLocalVariable", 2, EXAMPLE)),
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
        new LearningPrompt("pmd:UnusedLocalVariable", "pmd", "UnusedLocalVariable", 2, EXAMPLE)
            .instruction();

    assertThat(new ForyJsonCodec().toJson(output)).isEqualTo(fixture("hook-posttooluse.json"));
  }
}
