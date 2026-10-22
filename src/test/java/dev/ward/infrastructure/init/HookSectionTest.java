package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import org.junit.jupiter.api.Test;

/** Root sections keep their original text; nested sections are keyed and scoped to the project. */
class HookSectionTest {
  private static final String API = "apps/api";
  private static final String PRE_COMMIT = "pre-commit";

  @Test
  void rootSectionIsUnchangedByteForByte() {
    assertThat(new HookSection("").render(PRE_COMMIT))
        .isEqualTo(
            "# ward-owned: quality-hooks v1 begin\n"
                + "if ! ward check --profile pre-commit --format json; then\n"
                + "  exit 1\n"
                + "fi\n"
                + "# ward-owned: quality-hooks v1 end\n");
  }

  @Test
  void nestedPreCommitRunsFromTheProjectOnlyWhenItIsStaged() {
    assertThat(new HookSection(API).render(PRE_COMMIT))
        .isEqualTo(
            "# ward-owned: quality-hooks v1 begin apps/api\n"
                + "if ! git diff --cached --quiet -- 'apps/api'; then\n"
                + "  if ! (cd \"$(git rev-parse --show-toplevel)\"/'apps/api'"
                + " && ward check --profile pre-commit --format json); then\n"
                + "    exit 1\n"
                + "  fi\n"
                + "fi\n"
                + "# ward-owned: quality-hooks v1 end apps/api\n");
  }

  @Test
  void nestedPrePushComparesWithTheUpstreamWithoutReadingStdin() {
    final String section = new HookSection(API).render("pre-push");

    assertThat(section)
        .contains(
            "if ! git rev-parse --verify --quiet '@{upstream}' >/dev/null"
                + " || ! git diff --quiet '@{upstream}...HEAD' -- 'apps/api'; then",
            "ward check --profile pre-push --format json")
        .doesNotContain("read ", "cat");
  }

  @Test
  void pathsThatCannotBeQuotedAreRejected() {
    assertThatThrownBy(() -> new HookSection("apps/it's"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("quote or line break");
    assertThatThrownBy(() -> new HookSection("apps/a\nb")).isInstanceOf(WardException.class);
  }
}
