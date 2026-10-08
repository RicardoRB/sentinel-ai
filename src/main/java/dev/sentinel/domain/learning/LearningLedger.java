package dev.sentinel.domain.learning;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record LearningLedger(int formatVersion, Map<String, LearningRecord> records) {
  public static final int CURRENT_FORMAT_VERSION = 1;

  public LearningLedger {
    if (formatVersion != CURRENT_FORMAT_VERSION) {
      throw new IllegalArgumentException("Unsupported learning format version: " + formatVersion);
    }
    Objects.requireNonNull(records);
    final var copy = new LinkedHashMap<String, LearningRecord>();
    records.forEach(
        (key, value) -> {
          if (!key.equals(value.key())) {
            throw new IllegalArgumentException("Learning record key does not match its value");
          }
          copy.put(key, Objects.requireNonNull(value));
        });
    records = Map.copyOf(copy);
  }

  public static LearningLedger empty() {
    return new LearningLedger(CURRENT_FORMAT_VERSION, Map.of());
  }
}
