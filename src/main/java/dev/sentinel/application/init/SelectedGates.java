package dev.sentinel.application.init;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.InitSelection;
import dev.sentinel.domain.project.Project;
import java.util.List;
import java.util.stream.Stream;

/** Resolves a selection's gate ids into configured gates; shared by planning and applying. */
final class SelectedGates {
  private SelectedGates() {}

  /**
   * Returns the selected gates with the selected preset gates first, in preset order, followed by
   * the other selected gates and the OpenSpec validations. Unselected preset gates are not added.
   */
  static List<InitGateOption> resolve(
      final InitSetupCatalog catalog,
      final Project project,
      final InitSelection selection,
      final String openSpecExecutable) {
    final List<InitGateOption> gates =
        Stream.concat(
                (selection.preset() == null
                        ? selection.gates()
                        : selection.preset().orderSelected(selection.gates()))
                    .stream(),
                selection.openSpecValidations().stream())
            .distinct()
            .map(
                id ->
                    catalog.openSpecValidations().contains(id)
                        ? catalog.openSpecValidation(project, id, openSpecExecutable)
                        : catalog.gate(project, id))
            .toList();
    if (gates.isEmpty() && selection.tools().isEmpty()) {
      throw new SentinelException("Select at least one quality gate.");
    }
    return gates;
  }
}
