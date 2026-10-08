package dev.sentinel.application.learning;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.learning.LearningLedger;
import dev.sentinel.domain.learning.LearningOutcome;
import dev.sentinel.domain.learning.LearningRecord;
import dev.sentinel.domain.learning.LearningState;
import dev.sentinel.domain.learning.LearningStore;
import dev.sentinel.domain.learning.LearningStoreException;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LearningServiceTest {
  private static final Project PROJECT =
      new Project(Path.of("/project"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void promptsArePropagatedAndResolvedLedgerIsSaved() {
    final LearningRecord failing =
        new LearningRecord(
            "checkstyle:FAILED",
            "checkstyle",
            "FAILED",
            "line too long",
            2,
            LearningState.FAILING,
            NOW.minusSeconds(10),
            NOW,
            false);
    final FakeStore store = new FakeStore(new LearningLedger(1, Map.of(failing.key(), failing)));

    final LearningOutcome outcome = new LearningService(store, CLOCK).learn(passingReport(), 2);

    assertThat(outcome.prompts())
        .singleElement()
        .satisfies(
            prompt -> {
              assertThat(prompt.gate()).isEqualTo("checkstyle");
              assertThat(prompt.occurrences()).isEqualTo(2);
              assertThat(prompt.instruction()).contains("AGENTS.md");
            });
    assertThat(outcome.warnings()).isEmpty();
    assertThat(store.saved).isNotNull();
    assertThat(store.saved.records().get("checkstyle:FAILED").state())
        .isEqualTo(LearningState.RESOLVED);
    assertThat(store.saved.records().get("checkstyle:FAILED").prompted()).isTrue();
  }

  @Test
  void corruptStoreSkipsSaveAndReturnsWarningOnly() {
    final FakeStore store = new FakeStore(new LearningStoreException("could not read"));

    final LearningOutcome outcome = new LearningService(store, CLOCK).learn(passingReport(), 2);

    assertThat(outcome.prompts()).isEmpty();
    assertThat(outcome.warnings())
        .singleElement()
        .satisfies(warning -> assertThat(warning).contains("Learning disabled"));
    assertThat(store.saved).isNull();
    assertThat(store.saveCalls).isZero();
  }

  @Test
  void thresholdIsRespectedWhenBuildingPrompts() {
    final LearningRecord failing =
        new LearningRecord(
            "checkstyle:FAILED",
            "checkstyle",
            "FAILED",
            "line too long",
            1,
            LearningState.FAILING,
            NOW.minusSeconds(10),
            NOW,
            false);
    final FakeStore store = new FakeStore(new LearningLedger(1, Map.of(failing.key(), failing)));

    final LearningOutcome outcome = new LearningService(store, CLOCK).learn(passingReport(), 2);

    assertThat(outcome.prompts()).isEmpty();
    assertThat(store.saved.records().get("checkstyle:FAILED").state())
        .isEqualTo(LearningState.RESOLVED);
  }

  private static CheckReport passingReport() {
    return new CheckReport(
        PROJECT,
        List.of(
            new GateResult(
                "checkstyle", GateStatus.PASSED, List.of("checkstyle"), 0, Duration.ZERO, "", "")));
  }

  private static final class FakeStore implements LearningStore {
    private final Optional<LearningLedger> loaded;
    private final LearningStoreException failure;
    private LearningLedger saved;
    private int saveCalls;

    FakeStore(final LearningLedger ledger) {
      this.loaded = Optional.of(ledger);
      this.failure = null;
    }

    FakeStore(final LearningStoreException failure) {
      this.loaded = Optional.empty();
      this.failure = failure;
    }

    @Override
    public Optional<LearningLedger> load(Path projectRoot) throws LearningStoreException {
      if (failure != null) {
        throw failure;
      }
      return loaded;
    }

    @Override
    public void save(Path projectRoot, final LearningLedger ledger) throws LearningStoreException {
      saveCalls++;
      saved = ledger;
    }
  }
}
