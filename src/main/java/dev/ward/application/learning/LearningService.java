package dev.ward.application.learning;

import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.learning.LearningLedger;
import dev.ward.domain.learning.LearningOutcome;
import dev.ward.domain.learning.LearningPolicy;
import dev.ward.domain.learning.LearningStore;
import dev.ward.domain.learning.LearningStoreException;
import java.time.Clock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Updates the persisted learning ledger after a check and returns prompts for repeated failures.
 */
public final class LearningService {
  private static final Logger LOGGER = Logger.getLogger(LearningService.class.getName());
  private final LearningStore store;
  private final Clock clock;

  /** Creates the service using the system UTC clock. */
  @Inject
  public LearningService(final LearningStore store) {
    this(store, Clock.systemUTC());
  }

  /** Creates the service; {@code clock} timestamps the records. */
  public LearningService(final LearningStore store, final Clock clock) {
    this.store = store;
    this.clock = clock;
  }

  /**
   * Records the results of {@code report} and prompts for failures repeated {@code threshold}
   * times. A store failure disables learning for this run and is returned as a warning.
   */
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
