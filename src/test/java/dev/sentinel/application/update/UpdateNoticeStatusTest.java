package dev.sentinel.application.update;

import static dev.sentinel.application.update.UpdateNoticeFixtures.CURRENT;
import static dev.sentinel.application.update.UpdateNoticeFixtures.NEWER;
import static dev.sentinel.application.update.UpdateNoticeFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.update.CachedCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class UpdateNoticeStatusTest {
  private final UpdateNoticeFixtures fixtures = new UpdateNoticeFixtures();

  @Test
  void developmentBuildSkipsTheLookup() {
    final UpdateNoticeService snapshot =
        fixtures.service("0.4.0-SNAPSHOT", Optional.of(NEWER), Map.of(), false);

    assertThat(snapshot.automaticNotice()).isEmpty();
    assertThat(snapshot.freshStatus().state()).isEqualTo(UpdateStatus.State.SKIPPED);
    assertThat(fixtures.lookups).hasValue(0);
  }

  @Test
  void freshStatusIgnoresTheCache() {
    fixtures.cache.stored = Optional.of(new CachedCheck(NOW, CURRENT));

    final UpdateStatus status = fixtures.service(Optional.of(NEWER)).freshStatus();

    assertThat(status.state()).isEqualTo(UpdateStatus.State.OUTDATED);
    assertThat(status.message()).startsWith("v0.4.0 is available (you have v0.3.1). Update:");
    assertThat(fixtures.lookups).hasValue(1);
  }

  @Test
  void freshStatusReportsCurrentUnknownAndOptOut() {
    assertThat(fixtures.service(Optional.of(CURRENT)).freshStatus())
        .isEqualTo(new UpdateStatus(UpdateStatus.State.CURRENT, "v0.3.1 is the latest release."));
    assertThat(fixtures.service(Optional.empty()).freshStatus().state())
        .isEqualTo(UpdateStatus.State.UNKNOWN);
    assertThat(
            fixtures
                .service(
                    "0.3.1", Optional.of(NEWER), Map.of(UpdateNoticeService.OPT_OUT, "1"), false)
                .freshStatus()
                .state())
        .isEqualTo(UpdateStatus.State.SKIPPED);
    assertThat(fixtures.lookups).hasValue(2);
  }

  @Test
  void logsDecisionsWithoutEnvironmentValues() {
    final List<LogRecord> records = new ArrayList<>();
    final Handler handler =
        new Handler() {
          @Override
          public void publish(final LogRecord logRecord) {
            records.add(logRecord);
          }

          @Override
          public void flush() {
            // Records are kept in memory.
          }

          @Override
          public void close() {
            // Nothing to release.
          }
        };
    final Logger logger = Logger.getLogger(UpdateNoticeService.class.getName());
    final Level previous = logger.getLevel();
    logger.addHandler(handler);
    logger.setLevel(Level.FINE);
    try {
      fixtures.service(Optional.empty()).automaticNotice();
      fixtures.service(Optional.empty()).automaticNotice();
      fixtures
          .service("0.3.1", Optional.of(NEWER), Map.of("CI", "secret-value"), false)
          .automaticNotice();
    } finally {
      logger.removeHandler(handler);
      logger.setLevel(previous);
    }

    assertThat(records)
        .extracting(LogRecord::getMessage)
        .containsExactly(
            "event=update-check status=looked-up result=unknown",
            "event=update-check status=cached",
            "event=update-check status=skipped reason=ci");
  }
}
