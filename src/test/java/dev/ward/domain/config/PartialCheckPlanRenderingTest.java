package dev.ward.domain.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** How narrowed paths are rendered into arguments, and how patterns match them. */
class PartialCheckPlanRenderingTest {
  @Test
  void withoutSeparatorEachPathIsItsOwnArgument() {
    assertThat(
            PartialPlans.plan(
                PartialPlans.gate(
                    PartialPlans.partial(List.of("**"), List.of(), List.of("{paths}"), null, null)),
                "src/A.java",
                "src/B.java"))
        .isEqualTo(PartialPlans.narrowed("src/A.java", "src/B.java"));
  }

  @Test
  void separatorJoinsTheRenderedItemsIntoOneArgument() {
    assertThat(
            PartialPlans.plan(
                PartialPlans.gate(
                    PartialPlans.partial(
                        PartialPlans.JAVA,
                        PartialPlans.RULES,
                        PartialPlans.INCLUDES,
                        "**/{name}",
                        ",")),
                "src/main/java/a/A.java",
                "src/main/java/b/B.java"))
        .isEqualTo(PartialPlans.narrowed("-Dcheckstyle.includes=**/A.java,**/B.java"));
  }

  @Test
  void pathItemsAreRootRelativeWithForwardSlashes() {
    assertThat(
            PartialPlans.plan(
                PartialPlans.gate(
                    PartialPlans.partial(
                        PartialPlans.JAVA,
                        List.of(),
                        List.of("-DspotlessFiles={paths}"),
                        ".*/{path}",
                        ",")),
                PartialPlans.ORDER))
        .isEqualTo(PartialPlans.narrowed("-DspotlessFiles=.*/" + PartialPlans.ORDER));
  }

  @Test
  void argumentsWithoutThePlaceholderAreAppendedUnchanged() {
    assertThat(
            PartialPlans.plan(
                PartialPlans.gate(
                    PartialPlans.partial(
                        PartialPlans.JAVA,
                        List.of(),
                        List.of("-Pward.partial=true", "-PwardPaths={paths}"),
                        null,
                        ",")),
                "src/A.java",
                "src/B.java"))
        .isEqualTo(
            PartialPlans.narrowed("-Pward.partial=true", "-PwardPaths=src/A.java,src/B.java"));
  }

  @Test
  void shellCharactersStayLiteralArgumentText() {
    final String path = "src/odd name;$HOME.java";

    assertThat(
            PartialPlans.plan(
                PartialPlans.gate(
                    PartialPlans.partial(
                        PartialPlans.JAVA, List.of(), List.of("{paths}"), null, null)),
                path))
        .isEqualTo(PartialPlans.narrowed(path));
  }

  @Test
  void patternsMatchAcrossDirectoriesOnlyWithDoubleStar() {
    final GateConfiguration gate =
        PartialPlans.gate(
            PartialPlans.partial(
                List.of("src/**/*.java", "docs/?.md"), List.of(), List.of(), null, null));

    assertThat(PartialPlans.plan(gate, "src/A.java").scope())
        .isEqualTo(PartialCheckPlan.Scope.FULL);
    assertThat(PartialPlans.plan(gate, PartialPlans.ORDER).scope())
        .isEqualTo(PartialCheckPlan.Scope.FULL);
    assertThat(PartialPlans.plan(gate, "docs/a.md").scope()).isEqualTo(PartialCheckPlan.Scope.FULL);
    assertThat(PartialPlans.plan(gate, "docs/ab.md").scope())
        .isEqualTo(PartialCheckPlan.Scope.SKIP);
    assertThat(PartialPlans.plan(gate, "src/A.javax").scope())
        .isEqualTo(PartialCheckPlan.Scope.SKIP);
    assertThat(PartialPlans.plan(gate, "lib/src/A.java").scope())
        .isEqualTo(PartialCheckPlan.Scope.SKIP);
  }
}
