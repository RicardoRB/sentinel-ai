package dev.sentinel.infrastructure.cli.init;

import dev.sentinel.application.init.InitSetupCatalog;
import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.init.ArchitectureTestChange;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.InitResult;
import dev.sentinel.domain.project.Project;
import java.io.PrintWriter;
import java.util.List;

/** Prints the outcome of {@code sentinel init}. */
final class InitResultPrinter {
  private final PrintWriter out;

  InitResultPrinter(final PrintWriter out) {
    this.out = out;
  }

  void printSelectedGates(
      final InitSetupCatalog catalog, final Project project, final List<String> selectedGates) {
    for (final String selectedGate : selectedGates) {
      final InitGateOption gateOption = catalog.gate(project, selectedGate);
      out.printf(
          "Selected quality gate '%s': %s%n",
          gateOption.id(), gateOption.available() ? "AVAILABLE" : "UNAVAILABLE");
      out.println(gateOption.availabilityMessage());
    }
  }

  void print(final InitResult result) {
    if (!result.created()) {
      out.println(result.file() + " already exists. Nothing was changed.");
      return;
    }
    out.println((result.overwritten() ? "Updated " : "Created ") + result.file());
    if (!result.pomChanges().isEmpty()) {
      out.println("Updated pom.xml with Maven tools:");
      result.pomChanges().forEach(tool -> out.println("- " + tool));
    }
    result
        .preservedRuleFiles()
        .forEach(file -> out.println("Preserved user-owned rule file: " + file));
    result.pomWarnings().forEach(warning -> out.println("Warning: " + warning));
    printArchitectureTest(result.architectureTest());
    printIntegrations(result);
  }

  private void printArchitectureTest(final ArchitectureTestChange test) {
    if (test == null) {
      return;
    }
    if (test.created()) {
      out.println("Generated " + test.architecture() + " ArchUnit test: " + test.file());
    } else {
      out.println("Preserved existing ArchUnit test: " + test.file());
    }
  }

  private void printIntegrations(final InitResult result) {
    if (result.integrations().isEmpty()) {
      out.println("No agent integration installed.");
      return;
    }
    for (final IntegrationResult integration : result.integrations()) {
      out.printf("Integration: %s%n", integration.message());
      integration.changed().forEach(out::println);
    }
  }
}
