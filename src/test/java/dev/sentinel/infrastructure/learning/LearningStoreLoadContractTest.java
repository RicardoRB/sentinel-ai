package dev.sentinel.infrastructure.learning;

import static dev.sentinel.infrastructure.learning.LearningJsonContractFixtures.TIME;
import static dev.sentinel.infrastructure.learning.LearningJsonContractFixtures.fixture;
import static dev.sentinel.infrastructure.learning.LearningJsonContractFixtures.recordsOf;
import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.learning.LearningLedger;
import dev.sentinel.domain.learning.LearningRecord;
import dev.sentinel.domain.learning.LearningState;
import dev.sentinel.infrastructure.json.ForyJsonCodec;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Contract verification that the learning store fixture loads back to the same values. */
class LearningStoreLoadContractTest {

  @Test
  void storeLoadsContractFixtureBackToSameValues() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-load");
    Files.createDirectories(root.resolve(".sentinel"));
    Files.writeString(root.resolve(".sentinel/learning.json"), fixture("store-ledger.json"));
    final LearningLedger loaded =
        new JsonLearningStore(new ForyJsonCodec()).load(root).orElseThrow();

    new JsonLearningStore(new ForyJsonCodec()).save(root, loaded);
    assertThat(recordsOf(Files.readString(root.resolve(".sentinel/learning.json"))))
        .isEqualTo(recordsOf(fixture("store-ledger.json")));

    final LearningRecord unicode = loaded.records().get("pmd:UnusedLocalVariable");
    assertThat(unicode.summary()).isEqualTo("Unicode ✓ and \"quotes\" — строка");
    assertThat(unicode.occurrences()).isEqualTo(2);
    assertThat(unicode.state()).isEqualTo(LearningState.RESOLVED);
    assertThat(unicode.firstSeen()).isEqualTo(TIME);
    assertThat(unicode.lastSeen()).isEqualTo(TIME);
    assertThat(unicode.prompted()).isTrue();

    final LearningRecord escaped = loaded.records().get("checkstyle:MagicNumber");
    assertThat(escaped.summary()).isEqualTo("escapes \n \t \\");
    assertThat(escaped.state()).isEqualTo(LearningState.FAILING);
    assertThat(escaped.prompted()).isFalse();
  }
}
