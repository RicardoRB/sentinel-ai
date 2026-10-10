package dev.sentinel.infrastructure.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.domain.learning.LearningLedger;
import dev.sentinel.domain.learning.LearningRecord;
import dev.sentinel.domain.learning.LearningState;
import dev.sentinel.domain.learning.LearningStoreException;
import dev.sentinel.infrastructure.json.ForyJsonCodec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonLearningStoreTest {
  @Test
  void missingFileAndRoundTrip(@TempDir final Path root) throws Exception {
    final JsonLearningStore store = new JsonLearningStore(new ForyJsonCodec());
    assertThat(store.load(root)).isEmpty();
    final Instant time = Instant.parse("2026-01-01T00:00:00Z");
    final LearningRecord record =
        new LearningRecord(
            "tests:FAILED",
            "tests",
            "FAILED",
            "Unicode ✓ and \"quotes\"",
            2,
            LearningState.FAILING,
            time,
            time,
            false);
    store.save(root, new LearningLedger(1, Map.of(record.key(), record)));
    assertThat(store.load(root))
        .get()
        .extracting(LearningLedger::records)
        .satisfies(
            records -> {
              assertThat(records.get("tests:FAILED")).isEqualTo(record);
            });
    assertThat(Files.list(root.resolve(".sentinel")).toList()).singleElement();
  }

  @Test
  void rejectsCorruptAndUnsupportedDataWithoutReplacingFile(@TempDir final Path root)
      throws Exception {
    final Path file = root.resolve(".sentinel/learning.json");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "{not json}");
    assertThatThrownBy(() -> new JsonLearningStore(new ForyJsonCodec()).load(root))
        .isInstanceOf(LearningStoreException.class);
    assertThat(file).hasContent("{not json}");
    Files.writeString(file, "{\"formatVersion\":99,\"records\":{}}");
    assertThatThrownBy(() -> new JsonLearningStore(new ForyJsonCodec()).load(root))
        .isInstanceOf(LearningStoreException.class);
    assertThat(file).hasContent("{\"formatVersion\":99,\"records\":{}}");
  }

  @Test
  void rejectsInvalidRecordsWithoutReplacingFile(@TempDir final Path root) throws Exception {
    final Path file = root.resolve(".sentinel/learning.json");
    Files.createDirectories(file.getParent());

    final String nullRecord = "{\"formatVersion\":1,\"records\":{\"tests:FAILED\":null}}";
    Files.writeString(file, nullRecord);
    assertThatThrownBy(() -> new JsonLearningStore(new ForyJsonCodec()).load(root))
        .isInstanceOf(LearningStoreException.class);
    assertThat(file).hasContent(nullRecord);

    final String badState =
        "{\"formatVersion\":1,\"records\":{\"tests:FAILED\":{\"key\":\"tests:FAILED\","
            + "\"gate\":\"tests\",\"status\":\"FAILED\",\"summary\":\"boom\",\"occurrences\":1,"
            + "\"state\":\"BOGUS\",\"firstSeen\":\"2026-01-01T00:00:00Z\","
            + "\"lastSeen\":\"2026-01-01T00:00:00Z\",\"prompted\":false}}}";
    Files.writeString(file, badState);
    assertThatThrownBy(() -> new JsonLearningStore(new ForyJsonCodec()).load(root))
        .isInstanceOf(LearningStoreException.class);
    assertThat(file).hasContent(badState);

    final String mismatchedKey =
        "{\"formatVersion\":1,\"records\":{\"other:FAILED\":{\"key\":\"tests:FAILED\","
            + "\"gate\":\"tests\",\"status\":\"FAILED\",\"summary\":\"boom\",\"occurrences\":1,"
            + "\"state\":\"FAILING\",\"firstSeen\":\"2026-01-01T00:00:00Z\","
            + "\"lastSeen\":\"2026-01-01T00:00:00Z\",\"prompted\":false}}}";
    Files.writeString(file, mismatchedKey);
    assertThatThrownBy(() -> new JsonLearningStore(new ForyJsonCodec()).load(root))
        .isInstanceOf(LearningStoreException.class);
  }

  @Test
  void saveOverwritesAtomicallyAndLeavesNoTemporaryFiles(@TempDir final Path root)
      throws Exception {
    final JsonLearningStore store = new JsonLearningStore(new ForyJsonCodec());
    final Instant time = Instant.parse("2026-01-01T00:00:00Z");
    final LearningRecord first =
        new LearningRecord(
            "tests:FAILED", "tests", "FAILED", "one", 1, LearningState.FAILING, time, time, false);
    store.save(root, new LearningLedger(1, Map.of(first.key(), first)));

    final LearningRecord second =
        new LearningRecord(
            "tests:FAILED",
            "tests",
            "FAILED",
            "two",
            2,
            LearningState.FAILING,
            time,
            time.plusSeconds(1),
            false);
    store.save(root, new LearningLedger(1, Map.of(second.key(), second)));

    assertThat(store.load(root))
        .get()
        .extracting(LearningLedger::records)
        .satisfies(records -> assertThat(records.get("tests:FAILED")).isEqualTo(second));
    try (Stream<Path> entries = Files.list(root.resolve(".sentinel"))) {
      assertThat(entries.map(p -> p.getFileName().toString()).toList())
          .containsExactly("learning.json");
    }
  }

  @Test
  void corruptLoadPerformsNoWritesUnderSentinelDirectory(@TempDir final Path root)
      throws Exception {
    final Path file = root.resolve(".sentinel/learning.json");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "{not json}");
    assertThatThrownBy(() -> new JsonLearningStore(new ForyJsonCodec()).load(root))
        .isInstanceOf(LearningStoreException.class);
    try (Stream<Path> entries = Files.list(root.resolve(".sentinel"))) {
      assertThat(entries.map(p -> p.getFileName().toString()).toList())
          .containsExactly("learning.json");
    }
  }
}
