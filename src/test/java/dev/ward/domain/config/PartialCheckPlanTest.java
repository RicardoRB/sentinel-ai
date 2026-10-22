package dev.ward.domain.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Which gates a partial check skips, narrows, or runs in full. */
class PartialCheckPlanTest {
  @Test
  void gateWithoutPartialTableRunsInFull() {
    assertThat(PartialPlans.plan(PartialPlans.gate(null), "README.md"))
        .isEqualTo(PartialPlans.full());
  }

  @Test
  void irrelevantEditSkipsTheGate() {
    assertThat(
            PartialPlans.plan(
                PartialPlans.gate(
                    PartialPlans.partial(
                        PartialPlans.JAVA, PartialPlans.RULES, List.of(), null, null)),
                "README.md"))
        .isEqualTo(
            new PartialCheckPlan.GatePlan(
                PartialPlans.GATE, PartialCheckPlan.Scope.SKIP, List.of()));
  }

  @Test
  void configurationEditRunsTheGateInFull() {
    assertThat(
            PartialPlans.plan(
                PartialPlans.gate(
                    PartialPlans.partial(
                        PartialPlans.JAVA,
                        PartialPlans.RULES,
                        PartialPlans.INCLUDES,
                        "**/{name}",
                        ",")),
                "config/checkstyle.xml"))
        .isEqualTo(PartialPlans.full());
  }

  @Test
  void configurationEditAlongsideASourceEditStillRunsInFull() {
    assertThat(
            PartialPlans.plan(
                PartialPlans.gate(
                    PartialPlans.partial(
                        PartialPlans.JAVA,
                        PartialPlans.RULES,
                        PartialPlans.INCLUDES,
                        "**/{name}",
                        ",")),
                PartialPlans.ORDER,
                "config/checkstyle.xml"))
        .isEqualTo(PartialPlans.full());
  }

  @Test
  void sourceEditNarrowsTheGateByFileName() {
    assertThat(
            PartialPlans.plan(
                PartialPlans.gate(
                    PartialPlans.partial(
                        PartialPlans.JAVA,
                        PartialPlans.RULES,
                        PartialPlans.INCLUDES,
                        "**/{name}",
                        ",")),
                PartialPlans.ORDER))
        .isEqualTo(PartialPlans.narrowed("-Dcheckstyle.includes=**/Order.java"));
  }

  @Test
  void relevantGateWithoutArgumentsRunsInFull() {
    assertThat(
            PartialPlans.plan(
                PartialPlans.gate(
                    PartialPlans.partial(
                        PartialPlans.JAVA, PartialPlans.RULES, List.of(), null, null)),
                PartialPlans.ORDER))
        .isEqualTo(PartialPlans.full());
  }

  @Test
  void deletedSourceRunsTheGateInFull() {
    assertThat(
            PartialCheckPlan.create(
                    PartialPlans.ROOT,
                    Map.of(
                        PartialPlans.GATE,
                        PartialPlans.gate(
                            PartialPlans.partial(
                                PartialPlans.JAVA,
                                PartialPlans.RULES,
                                PartialPlans.INCLUDES,
                                "**/{name}",
                                ","))),
                    List.of(PartialPlans.ROOT.resolve("src/B.java")),
                    path -> false)
                .gates()
                .get(PartialPlans.GATE))
        .isEqualTo(PartialPlans.full());
  }

  @Test
  void deletedFileIsNotPassedToTheTool() {
    final Path deleted = PartialPlans.ROOT.resolve("src/B.java");

    assertThat(
            PartialCheckPlan.create(
                    PartialPlans.ROOT,
                    Map.of(
                        PartialPlans.GATE,
                        PartialPlans.gate(
                            PartialPlans.partial(
                                PartialPlans.JAVA, List.of(), List.of("{paths}"), null, null))),
                    List.of(PartialPlans.ROOT.resolve("src/A.java"), deleted),
                    path -> !path.equals(deleted))
                .gates()
                .get(PartialPlans.GATE))
        .isEqualTo(PartialPlans.narrowed("src/A.java"));
  }

  @Test
  void eventWithoutPathsRunsEveryGateInFull() {
    final PartialCheckPlan plan =
        PartialCheckPlan.create(
            PartialPlans.ROOT,
            Map.of(
                PartialPlans.GATE,
                PartialPlans.gate(
                    PartialPlans.partial(
                        PartialPlans.JAVA, PartialPlans.RULES, PartialPlans.INCLUDES, null, ","))),
            List.of(),
            PartialPlans.EVERYTHING_EXISTS);

    assertThat(plan.gates().get(PartialPlans.GATE)).isEqualTo(PartialPlans.full());
  }

  @Test
  void pathOutsideTheProjectSkipsPartialGatesAndRunsTheOthers() {
    final Map<String, GateConfiguration> gates = new LinkedHashMap<>();
    gates.put(
        PartialPlans.GATE,
        PartialPlans.gate(
            PartialPlans.partial(List.of("**"), List.of(), PartialPlans.INCLUDES, null, ",")));
    gates.put("tests", PartialPlans.gate(null));

    final PartialCheckPlan plan =
        PartialCheckPlan.create(
            PartialPlans.ROOT,
            gates,
            List.of(PartialPlans.ROOT.resolveSibling("other/Order.java")),
            PartialPlans.EVERYTHING_EXISTS);

    assertThat(plan.gates().get(PartialPlans.GATE).scope()).isEqualTo(PartialCheckPlan.Scope.SKIP);
    assertThat(plan.gates().get("tests").scope()).isEqualTo(PartialCheckPlan.Scope.FULL);
    assertThat(plan.count(PartialCheckPlan.Scope.SKIP)).isOne();
    assertThat(plan.count(PartialCheckPlan.Scope.FULL)).isOne();
    assertThat(plan.count(PartialCheckPlan.Scope.NARROW)).isZero();
  }
}
