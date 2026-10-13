package dev.sentinel.domain.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenSpecLayoutTest {

  @Test
  void changeFilesRouteToTheirChange() {
    assertThat(OpenSpecLayout.item(Path.of("openspec/changes/foo/proposal.md")))
        .contains(new SpecItem(SpecItem.Kind.CHANGE, "foo"));
    assertThat(OpenSpecLayout.item(Path.of("openspec/changes/foo/specs/x/spec.md")))
        .contains(new SpecItem(SpecItem.Kind.CHANGE, "foo"));
  }

  @Test
  void mainSpecsRouteToTheirCapabilityIncludingNestedPaths() {
    assertThat(OpenSpecLayout.item(Path.of("openspec/specs/auth/spec.md")))
        .contains(new SpecItem(SpecItem.Kind.SPEC, "auth"));
    assertThat(OpenSpecLayout.item(Path.of("openspec/specs/identity/user-auth/spec.md")))
        .contains(new SpecItem(SpecItem.Kind.SPEC, "identity/user-auth"));
  }

  @Test
  void archiveHiddenAndUnrelatedPathsAreIgnored() {
    assertThat(OpenSpecLayout.item(Path.of("openspec/changes/archive/2026-10-05-old/tasks.md")))
        .isEmpty();
    assertThat(OpenSpecLayout.item(Path.of("openspec/changes/.cache/x.md"))).isEmpty();
    assertThat(OpenSpecLayout.item(Path.of("openspec/changes/foo"))).isEmpty();
    assertThat(OpenSpecLayout.item(Path.of("openspec/specs/auth/notes.md"))).isEmpty();
    assertThat(OpenSpecLayout.item(Path.of("openspec/specs/spec.md"))).isEmpty();
    assertThat(OpenSpecLayout.item(Path.of("openspec/config.yaml"))).isEmpty();
    assertThat(OpenSpecLayout.item(Path.of("src/main/java/App.java"))).isEmpty();
    assertThat(OpenSpecLayout.item(Path.of("/abs/openspec/changes/foo/a.md"))).isEmpty();
    assertThat(OpenSpecLayout.item(Path.of("../openspec/changes/foo/a.md"))).isEmpty();
    assertThat(OpenSpecLayout.item(null)).isEmpty();
  }

  @Test
  void routingDeduplicatesInFirstSeenOrder() {
    assertThat(
            OpenSpecLayout.route(
                List.of(
                    Path.of("openspec/changes/foo/proposal.md"),
                    Path.of("openspec/changes/bar/design.md"),
                    Path.of("openspec/changes/foo/specs/x/spec.md"),
                    Path.of("openspec/specs/identity/user-auth/spec.md"),
                    Path.of("README.md"))))
        .containsExactly(
            new SpecItem(SpecItem.Kind.CHANGE, "foo"),
            new SpecItem(SpecItem.Kind.CHANGE, "bar"),
            new SpecItem(SpecItem.Kind.SPEC, "identity/user-auth"));
  }

  @Test
  void locationNamesTheRequiredDirectoryOrFile() {
    assertThat(OpenSpecLayout.location(new SpecItem(SpecItem.Kind.CHANGE, "foo")))
        .isEqualTo(Path.of("openspec/changes/foo"));
    assertThat(OpenSpecLayout.location(new SpecItem(SpecItem.Kind.SPEC, "identity/user-auth")))
        .isEqualTo(Path.of("openspec/specs/identity/user-auth/spec.md"));
  }

  @Test
  void itemKindsExposeToolTypeNames() {
    assertThat(SpecItem.Kind.CHANGE.type()).isEqualTo("change");
    assertThat(SpecItem.Kind.SPEC.type()).isEqualTo("spec");
  }
}
