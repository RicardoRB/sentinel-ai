package dev.sentinel.domain.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class GlobPatternTest {

  @Test
  void segmentsMatchLikeTheShell() {
    assertThat(GlobPattern.matches("*", "openspec")).isTrue();
    assertThat(GlobPattern.matches("sp?cs", "specs")).isTrue();
    assertThat(GlobPattern.matches("[sx]pecs", "specs")).isTrue();
    assertThat(GlobPattern.matches("[!s]pecs", "specs")).isFalse();
    assertThat(GlobPattern.matches("{specs,changes}", "changes")).isTrue();
    assertThat(GlobPattern.matches("*.log", "openspec")).isFalse();
    assertThat(GlobPattern.matches("[unclosed", "[unclosed")).isTrue();
    assertThat(GlobPattern.matches("a.b", "axb")).isFalse();
  }

  @Test
  void reachCountsMatchedLeadingNames() {
    final List<String> names = List.of("openspec", "specs");

    assertThat(GlobPattern.reach(List.of("*"), names)).isEqualTo(1);
    assertThat(GlobPattern.reach(List.of("*", "sp*"), names)).isEqualTo(2);
    assertThat(GlobPattern.reach(List.of("*", "sp*", "x"), names)).isEqualTo(2);
    assertThat(GlobPattern.reach(List.of("**"), names)).isEqualTo(2);
    assertThat(GlobPattern.reach(List.of("*.md"), names)).isEqualTo(-1);
  }

  @Test
  void firstPatternCharacterFindsUnquotedSpecials() {
    assertThat(GlobPattern.firstPatternCharacter("openspec/*")).isEqualTo(9);
    assertThat(GlobPattern.firstPatternCharacter("a/{b,c}")).isEqualTo(2);
    assertThat(GlobPattern.firstPatternCharacter("plain")).isEqualTo(-1);
  }
}
