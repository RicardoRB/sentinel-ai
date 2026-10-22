package dev.ward.domain.update;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReleaseVersionTest {
  private static final ReleaseVersion V1_4_0 = new ReleaseVersion(1, 4, 0);

  private static ReleaseVersion version(final String text) {
    return ReleaseVersion.parse(text).orElseThrow();
  }

  @Test
  void parsesPlainAndTagForms() {
    assertThat(ReleaseVersion.parse("1.4.0")).contains(V1_4_0);
    assertThat(ReleaseVersion.parse("v1.4.0")).contains(V1_4_0);
    assertThat(version("v1.4.0")).hasToString("1.4.0");
  }

  @Test
  void rejectsDevelopmentBuildsAndOtherShapes() {
    assertThat(ReleaseVersion.parse("0.5.0-SNAPSHOT")).isEmpty();
    assertThat(ReleaseVersion.parse("1.4")).isEmpty();
    assertThat(ReleaseVersion.parse("v1.4.0-rc1")).isEmpty();
    assertThat(ReleaseVersion.parse("")).isEmpty();
    assertThat(ReleaseVersion.parse(null)).isEmpty();
  }

  @Test
  void comparesEachNumberNumerically() {
    assertThat(version("0.10.0").isNewerThan(version("0.9.0"))).isTrue();
    assertThat(version("1.0.0").isNewerThan(version("0.99.99"))).isTrue();
    assertThat(version("0.9.2").isNewerThan(version("0.9.1"))).isTrue();
  }

  @Test
  void neverReportsEqualOrOlderAsNewer() {
    assertThat(version("0.9.0").isNewerThan(version("0.9.0"))).isFalse();
    assertThat(version("0.8.9").isNewerThan(version("0.9.0"))).isFalse();
  }
}
