package dev.sentinel.domain.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Edited paths are compared with the root in the canonical form the port returns. */
class PartialCheckPlanLinkTest {
  @Test
  void editedPathReachedThroughALinkIsMatchedAgainstTheCanonicalRoot() {
    final Path link = Path.of("/link/shop").toAbsolutePath();
    final PathExistence existence =
        new PathExistence() {
          @Override
          public boolean exists(final Path path) {
            return true;
          }

          @Override
          public Path canonical(final Path path) {
            final Path absolute = path.toAbsolutePath().normalize();
            return absolute.startsWith(link)
                ? PartialPlans.ROOT.resolve(link.relativize(absolute))
                : absolute;
          }
        };
    final PartialCheckPlan plan =
        PartialCheckPlan.create(
            PartialPlans.ROOT,
            Map.of(
                PartialPlans.GATE,
                PartialPlans.gate(
                    PartialPlans.partial(
                        PartialPlans.JAVA,
                        PartialPlans.RULES,
                        PartialPlans.INCLUDES,
                        "{path}",
                        null))),
            List.of(link.resolve(PartialPlans.ORDER)),
            existence);

    assertThat(plan.gates().get(PartialPlans.GATE))
        .isEqualTo(PartialPlans.narrowed("-Dcheckstyle.includes=" + PartialPlans.ORDER));
  }
}
