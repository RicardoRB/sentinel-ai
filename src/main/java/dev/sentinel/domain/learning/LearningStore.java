package dev.sentinel.domain.learning;

import java.nio.file.Path;
import java.util.Optional;

public interface LearningStore {
  Optional<LearningLedger> load(Path projectRoot) throws LearningStoreException;

  void save(Path projectRoot, LearningLedger ledger) throws LearningStoreException;
}
