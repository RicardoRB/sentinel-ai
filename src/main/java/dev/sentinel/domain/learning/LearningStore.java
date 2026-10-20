package dev.sentinel.domain.learning;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Persists learning ledgers; implementations report failures with {@link LearningStoreException}.
 */
public interface LearningStore {
  /** Loads the ledger of {@code projectRoot}, or returns empty when none was saved. */
  Optional<LearningLedger> load(Path projectRoot);

  /** Saves {@code ledger} for {@code projectRoot}. */
  void save(Path projectRoot, LearningLedger ledger);
}
