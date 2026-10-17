package dev.sentinel.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.agent.PlannedFileChange;
import dev.sentinel.domain.agent.PlannedFileChange.Action;
import dev.sentinel.domain.init.InitPlan;
import dev.sentinel.domain.init.PlannedInstallation;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InitPlanPrinterTest {
  private static final Path ROOT = Path.of("/project");

  private final StringWriter output = new StringWriter();

  @Test
  void rendersActionsDetailsAndInstallationRelativeToTheProject() {
    final InitPlan plan =
        new InitPlan(
            List.of(
                change("sentinel.toml", Action.CREATE),
                new PlannedFileChange(
                    ROOT.resolve("pom.xml"), Action.MODIFY, List.of("+ maven-pmd-plugin 3.28.0")),
                change("config/checkstyle.xml", Action.PRESERVE_USER_OWNED),
                change(".claude/settings.json", Action.CONFLICT),
                change(".claude/hooks/old", Action.REMOVE),
                change("config/pmd-ruleset.xml", Action.UNCHANGED)),
            Optional.of(
                new PlannedInstallation(
                    "openspec", "@fission-ai/openspec@1.14.1", ROOT.resolve(".sentinel/tools"))));

    new InitResultPrinter(new PrintWriter(output, true)).printPlan(plan, ROOT);

    assertThat(output.toString())
        .contains(
            "Sentinel will make these changes:",
            "  create     sentinel.toml",
            "  modify     pom.xml",
            "               + maven-pmd-plugin 3.28.0",
            "  keep       config/checkstyle.xml  (user-owned, not changed)",
            "  conflict   .claude/settings.json  (user-owned, blocks initialization)",
            "  remove     .claude/hooks/old",
            "  install    openspec @fission-ai/openspec@1.14.1 into .sentinel/tools")
        .doesNotContain("pmd-ruleset.xml");
  }

  @Test
  void emptyPlanSaysNothingWouldChange() {
    new InitResultPrinter(new PrintWriter(output, true))
        .printPlan(new InitPlan(List.of(change("pom.xml", Action.UNCHANGED)), null), ROOT);

    assertThat(output.toString()).contains("No files would change.");
  }

  private static PlannedFileChange change(final String path, final Action action) {
    return new PlannedFileChange(ROOT.resolve(path), action);
  }
}
