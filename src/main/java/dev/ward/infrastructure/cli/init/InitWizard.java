package dev.ward.infrastructure.cli.init;

import dev.ward.application.init.InitSetupCatalog;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.InitArchitectureOption;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.InitIntegrationOption;
import dev.ward.domain.init.InitToolOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.RawTerminal.RawSession;
import dev.ward.domain.project.Project;
import java.io.PrintWriter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Interactive questions asked by {@code ward init} when options do not answer them. */
final class InitWizard {
  static final String GATE_INPUT_ENDED =
      "Input ended before the gate selection. In non-interactive mode pass --preset or --gate,"
          + " or --yes to accept defaults.";
  static final String PROJECT_INPUT_ENDED =
      "Input ended before the project selection. In non-interactive mode pass --project for each"
          + " project to configure.";

  final LinePrompt prompt;
  private final Answering answering;
  private final Supplier<Optional<RawSession>> rawTerminal;
  private final PrintWriter out;

  InitWizard(
      final LinePrompt prompt,
      final Supplier<Optional<RawSession>> rawTerminal,
      final PrintWriter out) {
    this(prompt, rawTerminal, out, false);
  }

  InitWizard(
      final LinePrompt prompt,
      final Supplier<Optional<RawSession>> rawTerminal,
      final PrintWriter out,
      final boolean terminal) {
    this.prompt = prompt;
    this.rawTerminal = rawTerminal;
    this.out = out;
    this.answering = new Answering(terminal, out);
  }

  /** Asks for the preset; an empty answer selects {@code defaultChoice} (1 to 3). */
  QualityPreset selectPreset(final int defaultChoice) {
    return answering.ask(
        () -> {
          out.println("Select quality preset: 1) standard  2) strict  3) custom");
          out.print("Choose a preset [1-3, Enter=" + defaultChoice + "]: ");
          out.flush();
          final String value =
              prompt
                  .readLine("preset selection")
                  .map(String::trim)
                  .orElseThrow(() -> new WardException(GATE_INPUT_ENDED));
          return switch (value.isEmpty() ? String.valueOf(defaultChoice) : value) {
            case "1" -> QualityPreset.STANDARD;
            case "2" -> QualityPreset.STRICT;
            case "3" -> null;
            default ->
                throw new InvalidAnswerException("Invalid preset selection. Choose 1, 2, or 3.");
          };
        });
  }

  List<String> selectIntegrations(final InitSetupCatalog catalog) {
    final List<InitIntegrationOption> choices = catalog.integrations();
    final List<String> ids = choices.stream().map(InitIntegrationOption::id).toList();
    final Optional<List<String>> fromMenu =
        menu(
            new MenuSpec(
                "Select agent integrations",
                choices.stream().map(InitIntegrationOption::label).toList(),
                "Select at least one integration, or choose none.",
                Set.of(),
                false),
            ids);
    if (fromMenu.isPresent()) {
      return fromMenu.get();
    }
    return answering.ask(
        () -> {
          out.println(
              "Select agent integrations (enter numbers separated by spaces, then press Enter):");
          for (int i = 0; i < choices.size(); i++) {
            out.printf("> [ ] %d) %s%n", i + 1, choices.get(i).label());
          }
          out.print(
              "Toggle integrations with space-separated numbers [1-" + choices.size() + "]: ");
          out.flush();
          return prompt
              .readSelection(
                  ids,
                  List.of(),
                  "integration",
                  "Select at least one integration, or choose 1 for none.")
              .orElseGet(
                  () -> {
                    LinePrompt.logDefault("integrations");
                    return List.of(InitSetupCatalog.NO_INTEGRATION);
                  });
        });
  }

  /** Asks for the gates, starting with {@code preselected} checked. */
  List<String> selectGates(
      final InitSetupCatalog catalog, final Project project, final List<String> preselected) {
    final List<InitGateOption> choices = catalog.gates(project);
    final List<String> ids = choices.stream().map(InitGateOption::id).toList();
    final List<String> presetGates = preselected;
    final Optional<List<String>> fromMenu =
        menu(
            new MenuSpec(
                "Select quality gates",
                choices.stream().map(InitWizard::gateLabel).toList(),
                "Select at least one quality gate.",
                IntStream.range(0, ids.size())
                    .filter(i -> presetGates.contains(ids.get(i)))
                    .boxed()
                    .collect(Collectors.toSet()),
                false),
            ids);
    if (fromMenu.isPresent()) {
      return fromMenu.get();
    }
    return answering.ask(
        () -> {
          out.println(
              "Select quality gates (enter numbers separated by spaces, then press Enter):");
          for (int i = 0; i < choices.size(); i++) {
            final InitGateOption choice = choices.get(i);
            out.printf(
                "> %s %d) %s%n",
                presetGates.contains(choice.id()) ? "[x]" : "[ ]", i + 1, gateLabel(choice));
          }
          out.print(
              "Toggle quality gates with space-separated numbers [1-"
                  + choices.size()
                  + "]"
                  + (presetGates.isEmpty() ? ": " : ", or press Enter to accept: "));
          out.flush();
          return prompt
              .readSelection(ids, presetGates, "quality-gate", "Select at least one quality gate.")
              .orElseThrow(() -> new WardException(GATE_INPUT_ENDED));
        });
  }

  List<String> selectTools(final InitSetupCatalog catalog) {
    final List<InitToolOption> choices = catalog.tools();
    final List<String> ids = choices.stream().map(InitToolOption::id).toList();
    final Optional<List<String>> fromMenu =
        menu(
            new MenuSpec(
                "Select optional tools",
                choices.stream().map(c -> c.id() + " - " + c.description()).toList(),
                "",
                Set.of(),
                true),
            ids);
    if (fromMenu.isPresent()) {
      return fromMenu.get();
    }
    return answering.ask(
        () -> {
          out.println("Select optional tools (empty means none):");
          for (int i = 0; i < choices.size(); i++) {
            out.printf(
                "> [ ] %d) %s - %s%n", i + 1, choices.get(i).id(), choices.get(i).description());
          }
          out.print("Toggle tools with space-separated numbers [1-" + choices.size() + "]: ");
          out.flush();
          return prompt.readOptionalSelection(ids, "tool", "tools");
        });
  }

  List<String> selectOpenSpecValidations(final InitSetupCatalog catalog) {
    final List<String> ids = catalog.openSpecValidations();
    final Optional<List<String>> fromMenu =
        menu(
            new MenuSpec(
                "Select OpenSpec validations",
                List.of(
                    "openspec - active strict validation",
                    "openspec-archived - archived validation"),
                "",
                Set.of(),
                true),
            ids);
    if (fromMenu.isPresent()) {
      return fromMenu.get();
    }
    return answering.ask(
        () -> {
          out.println("Select OpenSpec validations (empty means none):");
          out.println("> [ ] 1) openspec - active strict validation");
          out.println("> [ ] 2) openspec-archived - archived validation");
          out.print("Toggle validations with space-separated numbers [1-2]: ");
          out.flush();
          return prompt.readOptionalSelection(ids, "OpenSpec validation", "openspec-validations");
        });
  }

  String selectArchitecture(final InitSetupCatalog catalog) {
    final List<InitArchitectureOption> choices = catalog.architectures();
    return answering.ask(
        () -> {
          out.println("Select an architecture style:");
          for (int i = 0; i < choices.size(); i++) {
            out.printf("> %d) %s%n", i + 1, choices.get(i).label());
          }
          out.print(
              "Choose an architecture [1-"
                  + choices.size()
                  + ", Enter="
                  + InitQuestions.DEFAULT_ARCHITECTURE
                  + "]: ");
          out.flush();
          final Optional<String> answer = prompt.readOrDefault("architecture");
          if (answer.isEmpty() || answer.get().isBlank()) {
            return InitQuestions.DEFAULT_ARCHITECTURE;
          }
          final int choice;
          try {
            choice = Integer.parseInt(answer.get().trim());
          } catch (NumberFormatException e) {
            throw new InvalidAnswerException(
                "Invalid architecture selection '" + answer.get() + "'.", e);
          }
          if (choice <= 0 || choice > choices.size()) {
            throw new InvalidAnswerException(
                "Invalid architecture selection. Choose a number from 1 to "
                    + choices.size()
                    + ".");
          }
          return choices.get(choice - 1).id();
        });
  }

  /**
   * Asks which discovered projects to configure; every row starts checked. {@code labels} are the
   * rows and {@code ids} the {@code --project} values they stand for.
   */
  List<String> selectProjects(final List<String> labels, final List<String> ids) {
    final Set<Integer> all = IntStream.range(0, ids.size()).boxed().collect(Collectors.toSet());
    final Optional<List<String>> fromMenu =
        menu(
            new MenuSpec(
                "Select the projects to configure",
                labels,
                "Select at least one project.",
                all,
                false),
            ids);
    if (fromMenu.isPresent()) {
      return fromMenu.get();
    }
    return answering.ask(
        () -> {
          out.println("Select the projects to configure:");
          for (int i = 0; i < labels.size(); i++) {
            out.printf("> [x] %d) %s%n", i + 1, labels.get(i));
          }
          out.print(
              "Toggle projects with space-separated numbers [1-"
                  + labels.size()
                  + "], or press Enter to accept: ");
          out.flush();
          return prompt
              .readSelection(ids, ids, "project", "Select at least one project.")
              .orElseThrow(() -> new WardException(PROJECT_INPUT_ENDED));
        });
  }

  /** A gate row: id and description, marked when its tool is not available. */
  private static String gateLabel(final InitGateOption gate) {
    return gate.id() + " - " + gate.description() + (gate.available() ? "" : " [unavailable]");
  }

  /** Runs a raw-key menu when a raw terminal is available and maps the chosen rows to ids. */
  private Optional<List<String>> menu(final MenuSpec spec, final List<String> ids) {
    final Optional<RawSession> raw = rawTerminal.get();
    if (raw.isEmpty()) {
      return Optional.empty();
    }
    try (RawSession session = raw.get()) {
      return Optional.of(
          new MultiSelectMenu().select(spec, session::read, out).stream().map(ids::get).toList());
    }
  }
}
