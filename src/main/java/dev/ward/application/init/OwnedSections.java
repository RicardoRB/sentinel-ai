package dev.ward.application.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.OwnedSection;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The Ward-owned sections a selection asks for: the CI workflow and the agent rules. */
final class OwnedSections {
  private OwnedSections() {}

  /**
   * Returns the sections in the order init writes them. {@code userOwnedRuleFiles} are rule files
   * init keeps as the user wrote them.
   */
  static List<OwnedSection> of(
      final InitSetupCatalog catalog,
      final Project project,
      final InitSelection selection,
      final List<InitGateOption> gates,
      final List<Path> userOwnedRuleFiles) {
    final List<OwnedSection> sections = new ArrayList<>();
    if (selection.ci() != null) {
      sections.add(
          CiWorkflowTemplate.render(selection.ci(), project, gates, catalog.wardVersion()));
    }
    if (selection.agentRules()) {
      sections.add(
          AgentRulesTemplate.render(project, gates, selection.preset(), userOwnedRuleFiles));
    }
    return sections;
  }
}
