package dev.ward.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.learning.LearningLedger;
import dev.ward.domain.learning.LearningRecord;
import dev.ward.domain.learning.LearningState;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.json.ForyJsonCodec;
import dev.ward.infrastructure.learning.JsonLearningStore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClaudeCodeHookPostToolUseTest extends ClaudeCodeHookTestSupport {
  @Test
  void bothFailuresReachTheAgent() {
    testsPass = false;
    specsValid = false;

    assertThat(hook(postToolUse("openspec/changes/foo/tasks.md"))).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("tests: ", "openspec change foo: 1 error");
  }

  @Test
  void learningPromptAndSpecFindingsAreBothReported() {
    final Instant seen = Instant.parse("2026-01-01T00:00:00Z");
    final LearningRecord failing =
        new LearningRecord(
            "tests:FinalParameters",
            "tests",
            "FinalParameters",
            "Parameter x should be final.",
            1,
            LearningState.FAILING,
            seen,
            seen,
            false);
    new JsonLearningStore(new ForyJsonCodec())
        .save(
            root,
            new LearningLedger(
                LearningLedger.CURRENT_FORMAT_VERSION, Map.of(failing.key(), failing)));
    specsValid = false;

    assertThat(hook(postToolUse("openspec/changes/foo/tasks.md"))).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString())
        .contains("openspec change foo: 1 error", "AGENTS.md", "FinalParameters");
    assertThat(out.toString()).isEmpty();
  }

  @Test
  void nonSpecEditKeepsTheExistingBehavior() {
    specsValid = false;

    assertThat(hook(postToolUse("src/main/java/App.java"))).isEqualTo(ExitCodes.OK);
    assertThat(commands).containsExactly(List.of("mvn", "test"));
    assertThat(out.toString()).isEmpty();
    assertThat(err.toString()).isEmpty();
  }
}
