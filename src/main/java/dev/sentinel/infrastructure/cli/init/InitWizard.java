package dev.sentinel.infrastructure.cli.init;

import dev.sentinel.application.init.InitSetupCatalog;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.InitArchitectureOption;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.InitIntegrationOption;
import dev.sentinel.domain.init.InitToolOption;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.init.RawTerminal.RawSession;
import dev.sentinel.domain.project.Project;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Interactive questions asked by {@code sentinel init} when options do not answer them. */
final class InitWizard {
  final LinePrompt prompt;
  private final Supplier<Optional<RawSession>> rawTerminal;
  private final PrintWriter out;

  InitWizard(
      final LinePrompt prompt,
      final Supplier<Optional<RawSession>> rawTerminal,
      final PrintWriter out) {
    this.prompt = prompt;
    this.rawTerminal = rawTerminal;
    this.out = out;
  }

  boolean confirmOverwrite(final Path file) {
    out.print(file + " already exists. Overwrite it? [y/N]: ");
    out.flush();
    final boolean overwrite = prompt.confirm();
    if (!overwrite) {
      out.println("Keeping existing sentinel.toml. No changes made.");
    }
    return overwrite;
  }

  boolean confirmOpenSpecInstallation(final Path destination) {
    out.println("OpenSpec is unavailable and can be installed locally.");
    out.println("Package: @fission-ai/openspec@1.14.1 (Node.js >=20.19.0)");
    out.println("Destination: " + destination);
    out.println("This may access the network and run npm lifecycle scripts.");
    out.print("Install OpenSpec? [y/N]: ");
    out.flush();
    return prompt.confirmInstallation();
  }

  QualityPreset selectPreset() {
    out.println("Select quality preset: 1) standard  2) strict  3) custom");
    out.print("Choose a preset [1-3]: ");
    out.flush();
    final String value = prompt.readLine("preset selection").map(String::trim).orElse("");
    return switch (value) {
      case "1" -> QualityPreset.STANDARD;
      case "2" -> QualityPreset.STRICT;
      case "3" -> null;
      default -> throw new SentinelException("Invalid preset selection. Choose 1, 2, or 3.");
    };
  }

  List<String> selectIntegrations(final InitSetupCatalog catalog) {
    final List<InitIntegrationOption> choices = catalog.integrations();
    final List<String> ids = choices.stream().map(InitIntegrationOption::id).toList();
    final Optional<List<String>> fromMenu =
        menu(
            "Select agent integrations",
            choices.stream().map(InitIntegrationOption::label).toList(),
            "Select at least one integration, or choose none.",
            Set.of(),
            ids);
    if (fromMenu.isPresent()) {
      return fromMenu.get();
    }
    out.println("Select agent integrations (enter numbers separated by spaces, then press Enter):");
    for (int i = 0; i < choices.size(); i++) {
      out.printf("> [ ] %d) %s%n", i + 1, choices.get(i).label());
    }
    out.print("Toggle integrations with space-separated numbers [1-" + choices.size() + "]: ");
    out.flush();
    return prompt.readSelection(
        ids, List.of(), "integration", "Select at least one integration, or choose 1 for none.");
  }

  List<String> selectGates(
      final InitSetupCatalog catalog, final Project project, final QualityPreset preset) {
    final List<InitGateOption> choices = catalog.gates(project);
    final List<String> ids = choices.stream().map(InitGateOption::id).toList();
    final List<String> presetGates = preset == null ? List.of() : preset.gates();
    final Optional<List<String>> fromMenu =
        menu(
            "Select quality gates",
            choices.stream().map(c -> c.id() + " - " + c.description()).toList(),
            "Select at least one quality gate.",
            IntStream.range(0, ids.size())
                .filter(i -> presetGates.contains(ids.get(i)))
                .boxed()
                .collect(Collectors.toSet()),
            ids);
    if (fromMenu.isPresent()) {
      return fromMenu.get();
    }
    out.println("Select quality gates (enter numbers separated by spaces, then press Enter):");
    for (int i = 0; i < choices.size(); i++) {
      final InitGateOption choice = choices.get(i);
      out.printf(
          "> [ ] %d) %s - %s [%s]%n",
          i + 1,
          choice.id(),
          choice.description(),
          choice.available() ? "available" : "unavailable");
    }
    out.print("Toggle quality gates with space-separated numbers [1-" + choices.size() + "]: ");
    out.flush();
    return prompt.readSelection(
        ids, presetGates, "quality-gate", "Select at least one quality gate.");
  }

  List<String> selectTools(final InitSetupCatalog catalog) {
    if (!prompt.hasMoreInput()) {
      return List.of();
    }
    final List<InitToolOption> choices = catalog.tools();
    final List<String> ids = choices.stream().map(InitToolOption::id).toList();
    final Optional<List<String>> fromMenu =
        menu(
            "Select optional tools",
            choices.stream().map(c -> c.id() + " - " + c.description()).toList(),
            "",
            Set.of(),
            ids,
            true);
    if (fromMenu.isPresent()) {
      return fromMenu.get();
    }
    out.println("Select optional tools (empty means none):");
    for (int i = 0; i < choices.size(); i++) {
      out.printf("> [ ] %d) %s - %s%n", i + 1, choices.get(i).id(), choices.get(i).description());
    }
    out.print("Toggle tools with space-separated numbers [1-" + choices.size() + "]: ");
    out.flush();
    return prompt.readOptionalSelection(ids, "tool");
  }

  List<String> selectOpenSpecValidations(final InitSetupCatalog catalog) {
    if (!prompt.hasMoreInput()) {
      return List.of();
    }
    final List<String> ids = catalog.openSpecValidations();
    final Optional<List<String>> fromMenu =
        menu(
            "Select OpenSpec validations",
            List.of(
                "openspec - active strict validation", "openspec-archived - archived validation"),
            "",
            Set.of(),
            ids,
            true);
    if (fromMenu.isPresent()) {
      return fromMenu.get();
    }
    out.println("Select OpenSpec validations (empty means none):");
    out.println("> [ ] 1) openspec - active strict validation");
    out.println("> [ ] 2) openspec-archived - archived validation");
    out.print("Toggle validations with space-separated numbers [1-2]: ");
    out.flush();
    return prompt.readOptionalSelection(ids, "OpenSpec validation");
  }

  String selectArchitecture(final InitSetupCatalog catalog) {
    final List<InitArchitectureOption> choices = catalog.architectures();
    out.println("Select an architecture style:");
    for (int i = 0; i < choices.size(); i++) {
      out.printf("> %d) %s%n", i + 1, choices.get(i).label());
    }
    out.print("Choose an architecture [1-" + choices.size() + "]: ");
    out.flush();
    final String value = prompt.requireLine("architecture selection");
    final int choice;
    try {
      choice = Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      throw new SentinelException("Invalid architecture selection '" + value + "'.", e);
    }
    if (choice <= 0 || choice > choices.size()) {
      throw new SentinelException(
          "Invalid architecture selection. Choose a number from 1 to " + choices.size() + ".");
    }
    return choices.get(choice - 1).id();
  }

  /** Runs a raw-key menu when a raw terminal is available and maps the chosen rows to ids. */
  private Optional<List<String>> menu(
      final String title,
      final List<String> labels,
      final String emptySelectionMessage,
      final Set<Integer> initialSelections,
      final List<String> ids) {
    return menu(title, labels, emptySelectionMessage, initialSelections, ids, false);
  }

  private Optional<List<String>> menu(
      final String title,
      final List<String> labels,
      final String emptySelectionMessage,
      final Set<Integer> initialSelections,
      final List<String> ids,
      final boolean allowEmpty) {
    final Optional<RawSession> raw = rawTerminal.get();
    if (raw.isEmpty()) {
      return Optional.empty();
    }
    try (RawSession session = raw.get()) {
      return Optional.of(
          new MultiSelectMenu()
                  .select(
                      title,
                      labels,
                      emptySelectionMessage,
                      session::read,
                      out,
                      initialSelections,
                      allowEmpty)
                  .stream()
                  .map(ids::get)
                  .toList());
    }
  }
}
