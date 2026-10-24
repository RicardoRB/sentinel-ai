package dev.ward.application.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** Resolves a selection's gate ids into configured gates; shared by planning and applying. */
final class SelectedGates {
  private static final String TRIVY = "trivy";
  private static final String OFFLINE_SCAN = "--offline-scan";

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
      throw new WardException("Select at least one quality gate.");
    }
    return offlineTrivy(gates, project);
  }

  /**
   * Maven Central rate-limits CI runners, so Trivy resolves POMs from the local repository when an
   * earlier Maven or Gradle gate has already filled it.
   */
  static List<InitGateOption> offlineTrivy(
      final List<InitGateOption> gates, final Project project) {
    if (project.toolchain() != Toolchain.JVM) {
      return gates;
    }
    final int firstBuild =
        IntStream.range(0, gates.size())
            .filter(i -> isBuildGate(gates.get(i), project))
            .findFirst()
            .orElse(gates.size());
    return IntStream.range(0, gates.size())
        .mapToObj(i -> i > firstBuild ? offline(gates.get(i)) : gates.get(i))
        .toList();
  }

  private static boolean isBuildGate(final InitGateOption gate, final Project project) {
    return !gate.command().isEmpty() && project.buildExecutable().equals(gate.command().getFirst());
  }

  private static InitGateOption offline(final InitGateOption gate) {
    if (!TRIVY.equals(gate.id()) || !isFilesystemScan(gate.command())) {
      return gate;
    }
    final List<String> command = new ArrayList<>(gate.command());
    command.add(2, OFFLINE_SCAN);
    return new InitGateOption(
        gate.id(), gate.description(), command, gate.available(), gate.availabilityMessage());
  }

  private static boolean isFilesystemScan(final List<String> command) {
    return command.size() > 1 && "fs".equals(command.get(1)) && !command.contains(OFFLINE_SCAN);
  }
}
