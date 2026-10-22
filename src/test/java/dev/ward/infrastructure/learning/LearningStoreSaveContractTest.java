package dev.ward.infrastructure.learning;

import static dev.ward.infrastructure.learning.LearningJsonContractFixtures.fixture;
import static dev.ward.infrastructure.learning.LearningJsonContractFixtures.ledger;
import static dev.ward.infrastructure.learning.LearningJsonContractFixtures.recordsOf;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.learning.LearningLedger;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Byte-for-byte contract verification of the persisted learning store. */
class LearningStoreSaveContractTest {

  @Test
  void storeSaveMatchesContractFixtureIncludingUnicodeAndTimestamps(@TempDir final Path root)
      throws Exception {
    final LearningLedger ledger = ledger();
    new JsonLearningStore(new ForyJsonCodec()).save(root, ledger);

    final String saved = Files.readString(root.resolve(".ward/learning.json"));
    // Record ordering is not contractual; content must match the fixture item for item.
    assertThat(recordsOf(saved)).isEqualTo(recordsOf(fixture("store-ledger.json")));
    assertThat(saved)
        .contains("Unicode ✓ and \\\"quotes\\\" — строка", "2026-01-01T00:00:00Z")
        .contains("escapes \\n \\t \\\\")
        .doesNotContain("stdout", "stderr", "command");
  }

  @Test
  void emptyCollectionsRoundTrip(@TempDir final Path root) throws Exception {
    final JsonLearningStore store = new JsonLearningStore(new ForyJsonCodec());
    store.save(root, LearningLedger.empty());
    assertThat(Files.readString(root.resolve(".ward/learning.json")))
        .isEqualTo("{\"formatVersion\":2,\"records\":{}}");
    assertThat(store.load(root)).get().satisfies(ledger -> assertThat(ledger.records()).isEmpty());
  }
}
