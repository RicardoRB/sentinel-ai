package dev.sentinel.domain.learning;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Persists learning ledgers; implementations report failures with {@link LearningStoreException}.
 */
public interface LearningStore {
  Optional<LearningLedger> load(Path projectRoot);

  void save(Path projectRoot, LearningLedger ledger);
}
