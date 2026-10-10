package dev.sentinel.application.learning;

import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.learning.LearningLedger;
import dev.sentinel.domain.learning.LearningOutcome;
import dev.sentinel.domain.learning.LearningPolicy;
import dev.sentinel.domain.learning.LearningStore;
import dev.sentinel.domain.learning.LearningStoreException;
import java.time.Clock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

public final class LearningService {
  private static final Logger LOGGER = Logger.getLogger(LearningService.class.getName());
  private final LearningStore store;
  private final Clock clock;

  @Inject
  public LearningService(final LearningStore store) {
    this(store, Clock.systemUTC());
  }

  public LearningService(final LearningStore store, final Clock clock) {
    this.store = store;
    this.clock = clock;
  }

  public LearningOutcome learn(final CheckReport report, final int threshold) {
    try {
      final LearningLedger ledger =
          store.load(report.project().root()).orElseGet(LearningLedger::empty);
      final LearningOutcome outcome =
          LearningPolicy.apply(ledger, report, threshold, clock.instant());
      store.save(report.project().root(), outcome.ledger());
      LOGGER.log(Level.INFO, () -> "event=learning-complete prompts=" + outcome.prompts().size());
      return outcome;
    } catch (LearningStoreException exception) {
      LOGGER.log(Level.WARNING, "event=learning-store-failure category=store-error");
      return LearningOutcome.withoutPrompts(
          LearningLedger.empty(), "Learning disabled for this run: " + exception.getMessage());
    }
  }
}
