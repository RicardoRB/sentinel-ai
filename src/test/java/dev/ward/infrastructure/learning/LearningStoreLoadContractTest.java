package dev.ward.infrastructure.learning;

import static dev.ward.infrastructure.learning.LearningJsonContractFixtures.TIME;
import static dev.ward.infrastructure.learning.LearningJsonContractFixtures.fixture;
import static dev.ward.infrastructure.learning.LearningJsonContractFixtures.recordsOf;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.learning.LearningLedger;
import dev.ward.domain.learning.LearningRecord;
import dev.ward.domain.learning.LearningState;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Contract verification that the learning store fixture loads back to the same values. */
class LearningStoreLoadContractTest {

  @Test
  void storeLoadsContractFixtureBackToSameValues() throws Exception {
    final Path root = Files.createTempDirectory("ward-load");
    Files.createDirectories(root.resolve(".ward"));
    Files.writeString(root.resolve(".ward/learning.json"), fixture("store-ledger.json"));
    final LearningLedger loaded =
        new JsonLearningStore(new ForyJsonCodec()).load(root).orElseThrow();

    new JsonLearningStore(new ForyJsonCodec()).save(root, loaded);
    assertThat(recordsOf(Files.readString(root.resolve(".ward/learning.json"))))
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
