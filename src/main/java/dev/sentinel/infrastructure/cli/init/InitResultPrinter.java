package dev.sentinel.infrastructure.cli.init;

import dev.sentinel.application.init.InitSetupCatalog;
import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.agent.PlannedFileChange;
import dev.sentinel.domain.init.ArchitectureTestChange;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.InitPlan;
import dev.sentinel.domain.init.InitResult;
import dev.sentinel.domain.init.PlannedGateInstallation;
import dev.sentinel.domain.project.Project;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Prints the outcome of {@code sentinel init}. */
final class InitResultPrinter {
  private static final Logger LOGGER = Logger.getLogger(InitResultPrinter.class.getName());
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
          gateOption.id(),
          gateOption.available()
              ? "AVAILABLE"
              : "UNAVAILABLE - " + gateOption.availabilityMessage());
      LOGGER.log(
          Level.INFO,
          () ->
              "event=init-gate-availability gate="
                  + gateOption.id()
                  + " status="
                  + (gateOption.available() ? "available" : "unavailable"));
      if ("dependency-check".equals(selectedGate) && !catalog.nvdApiKeySet(project)) {
        out.println(
            "Warning: NVD_API_KEY is not set; dependency-check scans may be very slow or rate-limited."
                + " Request a key from the NVD API.");
      }
    }
    if (selectedGates.contains("dependency-check") && selectedGates.contains("trivy")) {
      out.println(
          "Warning: trivy already scans dependencies for known vulnerabilities and needs no API"
              + " key; consider keeping trivy and dropping dependency-check.");
    }
  }

  /** Prints the files a plan would change, relative to the project root. */
  void printPlan(final InitPlan plan, final Path root) {
    final List<PlannedFileChange> files = plan.visibleFiles();
    if (files.isEmpty() && plan.installation().isEmpty() && plan.gateInstallations().isEmpty()) {
      out.println("No files would change.");
      return;
    }
    out.println("Sentinel will make these changes:");
    out.println();
    for (final PlannedFileChange file : files) {
      final String path = root.relativize(file.file()).toString();
      out.printf("  %-10s %s%s%n", verb(file.action()), path, note(file.action()));
      file.details().forEach(detail -> out.println("               " + detail));
    }
    plan.installation()
        .ifPresent(
            installation ->
                out.printf(
                    "  %-10s %s %s into %s%n",
                    "install",
                    installation.tool(),
                    installation.packageName(),
                    root.relativize(installation.destination())));
    for (final PlannedGateInstallation installation : plan.gateInstallations()) {
      if (installation.installable()) {
        out.printf("  %-10s %s%n", "install", String.join(" ", installation.command()));
      } else {
        out.printf("  %-10s %s: %s%n", "guidance", installation.tool(), installation.guidance());
      }
    }
    out.println();
  }

  private static String verb(final PlannedFileChange.Action action) {
    return switch (action) {
      case CREATE -> "create";
      case MODIFY -> "modify";
      case REMOVE -> "remove";
      case PRESERVE_USER_OWNED -> "keep";
      case CONFLICT -> "conflict";
      case UNCHANGED -> "unchanged";
    };
  }

  private static String note(final PlannedFileChange.Action action) {
    return switch (action) {
      case PRESERVE_USER_OWNED -> "  (user-owned, not changed)";
      case CONFLICT -> "  (user-owned, blocks initialization)";
      default -> "";
    };
  }

  /** Gradle gates run the wrapper or {@code gradle}; the result carries no build-file path. */
  private static boolean gradle(final InitResult result) {
    return result.gates().stream()
        .map(InitGateOption::command)
        .filter(command -> !command.isEmpty())
        .map(List::getFirst)
        .anyMatch(executable -> executable.endsWith("gradlew") || "gradle".equals(executable));
  }

  void print(final InitResult result) {
    if (!result.created()) {
      out.println(result.file() + " already exists. Nothing was changed.");
      return;
    }
    out.println((result.overwritten() ? "Updated " : "Created ") + result.file());
    if (!result.pomChanges().isEmpty()) {
      out.println(
          gradle(result)
              ? "Updated the Gradle build with tools:"
              : "Updated pom.xml with Maven tools:");
      result.pomChanges().forEach(tool -> out.println("- " + tool));
    }
    result
        .preservedRuleFiles()
        .forEach(file -> out.println("Preserved user-owned rule file: " + file));
    result.pomWarnings().forEach(warning -> out.println("Warning: " + warning));
    result.ownedSections().forEach(file -> out.println("Wrote Sentinel section: " + file));
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
