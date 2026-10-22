package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Root and nested sections coexist in one hook; each operation touches only its own section. */
class QualityHookSectionsTest {
  private static final Path HOOK = Path.of("pre-commit");
  private static final HookSection ROOT = new HookSection("");
  private static final HookSection API = new HookSection("apps/api");
  private static final HookSection BILLING = new HookSection("services/billing");
  private static final String USER = "#!/bin/sh\npnpm format\n";
  private static final String PROFILE = "pre-commit";

  @Test
  void sectionsOfDifferentProjectsCoexist() {
    final String hook = install(install(install(USER, ROOT), API), BILLING);

    assertThat(QualityHookFileOperations.contains(hook, ROOT)).isTrue();
    assertThat(QualityHookFileOperations.contains(hook, API)).isTrue();
    assertThat(QualityHookFileOperations.contains(hook, BILLING)).isTrue();
    assertThat(hook).startsWith("#!/bin/sh\n").endsWith("pnpm format\n");
    QualityHookFileOperations.rejectMalformed(HOOK, hook, ROOT);
    QualityHookFileOperations.rejectMalformed(HOOK, hook, API);
  }

  @Test
  void refreshingOneProjectKeepsTheOthers() {
    final String hook = install(install(USER, API), BILLING);

    assertThat(install(hook, API)).isEqualTo(hook);
    assertThat(install(hook, ROOT)).contains(API.start(), BILLING.start(), ROOT.start());
  }

  @Test
  void rootMarkerIsNotMistakenForANestedOne() {
    final String hook = install(USER, API);

    assertThat(QualityHookFileOperations.contains(hook, ROOT)).isFalse();
    QualityHookFileOperations.rejectMalformed(HOOK, hook, ROOT);
  }

  @Test
  void removingOneProjectKeepsTheOthersAndUserContent() {
    final String hook = install(install(install(USER, ROOT), API), BILLING);

    final String remaining = QualityHookFileOperations.removeSection(hook, API);

    assertThat(QualityHookFileOperations.contains(remaining, API)).isFalse();
    assertThat(remaining).contains(ROOT.start(), BILLING.start(), "pnpm format");
    final String withoutRoot = QualityHookFileOperations.removeSection(remaining, ROOT);
    assertThat(QualityHookFileOperations.contains(withoutRoot, ROOT)).isFalse();
    assertThat(withoutRoot).contains(BILLING.start());
  }

  @Test
  void duplicateMarkersAreMalformedOnlyForTheirProject() {
    final String hook = install(USER, ROOT) + API.start() + "\n" + API.start() + "\n";

    assertThatThrownBy(() -> QualityHookFileOperations.rejectMalformed(HOOK, hook, API))
        .isInstanceOf(WardException.class);
    QualityHookFileOperations.rejectMalformed(HOOK, hook, ROOT);
  }

  @Test
  void missingEndMarkerIsMalformed() {
    final String hook = USER + BILLING.start() + "\n";

    assertThatThrownBy(() -> QualityHookFileOperations.rejectMalformed(HOOK, hook, BILLING))
        .hasMessageContaining("Malformed");
  }

  private static String install(final String hook, final HookSection section) {
    return QualityHookFileOperations.update(hook, section, PROFILE);
  }
}
