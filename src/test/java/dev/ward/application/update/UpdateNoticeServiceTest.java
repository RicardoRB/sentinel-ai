package dev.ward.application.update;

import static dev.ward.application.update.UpdateNoticeFixtures.CURRENT;
import static dev.ward.application.update.UpdateNoticeFixtures.NEWER;
import static dev.ward.application.update.UpdateNoticeFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.update.CachedCheck;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UpdateNoticeServiceTest {
  private static final Duration DAY = Duration.ofHours(24);
  private static final Duration LESS_THAN_A_DAY = Duration.ofHours(23);
  private static final String RUNNING = "0.3.1";

  private final UpdateNoticeFixtures fixtures = new UpdateNoticeFixtures();

  @Test
  void staleCacheLooksUpAndRecordsTheResult() {
    fixtures.cache.stored = Optional.of(new CachedCheck(NOW.minus(DAY), CURRENT));

    final Optional<String> notice = fixtures.service(Optional.of(NEWER)).automaticNotice();

    assertThat(notice)
        .contains(
            "ward: v0.4.0 is available (you have v0.3.1). Update: curl -fsSL"
                + " https://raw.githubusercontent.com/RicardoRB/ward-ai/main/install.sh | sh");
    assertThat(fixtures.lookups).hasValue(1);
    assertThat(fixtures.cache.stored).contains(new CachedCheck(NOW, NEWER));
  }

  @Test
  void freshCacheDecidesWithoutLookingUp() {
    fixtures.cache.stored = Optional.of(new CachedCheck(NOW.minus(LESS_THAN_A_DAY), NEWER));

    assertThat(fixtures.service(Optional.of(CURRENT)).automaticNotice()).isPresent();
    assertThat(fixtures.lookups).hasValue(0);
  }

  @Test
  void missingOrCorruptCacheLooksUp() {
    assertThat(fixtures.service(Optional.of(NEWER)).automaticNotice()).isPresent();
    assertThat(fixtures.lookups).hasValue(1);
  }

  @Test
  void alreadyCurrentPrintsNothing() {
    assertThat(fixtures.service(Optional.of(CURRENT)).automaticNotice()).isEmpty();
  }

  @Test
  void unknownLatestIsCachedAsCurrentSoOfflineRunsWaitADay() {
    assertThat(fixtures.service(Optional.empty()).automaticNotice()).isEmpty();
    assertThat(fixtures.cache.stored).contains(new CachedCheck(NOW, CURRENT));
  }

  @Test
  void windowsNoticeShowsThePowerShellInstaller() {
    assertThat(fixtures.service(RUNNING, Optional.of(NEWER), Map.of(), true).automaticNotice())
        .contains(
            "ward: v0.4.0 is available (you have v0.3.1). Update: irm"
                + " https://raw.githubusercontent.com/RicardoRB/ward-ai/main/install.ps1 | iex");
  }

  @Test
  void optOutAndContinuousIntegrationSkipTheLookup() {
    assertThat(
            fixtures
                .service(
                    RUNNING, Optional.of(NEWER), Map.of(UpdateNoticeService.OPT_OUT, "1"), false)
                .automaticNotice())
        .isEmpty();
    assertThat(
            fixtures
                .service(RUNNING, Optional.of(NEWER), Map.of("CI", "true"), false)
                .automaticNotice())
        .isEmpty();
    assertThat(fixtures.lookups).hasValue(0);
  }

  @Test
  void emptyEnvironmentValuesDoNotOptOut() {
    assertThat(
            fixtures
                .service(
                    RUNNING,
                    Optional.of(NEWER),
                    Map.of(UpdateNoticeService.OPT_OUT, "", "CI", ""),
                    false)
                .automaticNotice())
        .isPresent();
  }
}
