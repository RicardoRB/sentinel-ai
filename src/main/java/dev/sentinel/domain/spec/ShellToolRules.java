package dev.sentinel.domain.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rules for tools whose effect depends on their subcommand or options. */
final class ShellToolRules {
  private static final Set<String> READ_ONLY_GIT =
      Set.of(
          "status",
          "diff",
          "log",
          "show",
          "blame",
          "ls-files",
          "ls-tree",
          "grep",
          "add",
          "rev-parse",
          "cat-file",
          "shortlog",
          "describe",
          "branch",
          "remote",
          "fetch",
          "config");
  private static final Set<String> DELETING_GIT = Set.of("rm", "clean");
  private static final Set<String> GIT_OPTIONS_WITH_VALUE =
      Set.of("-C", "-c", "--git-dir", "--work-tree", "--namespace", "--exec-path");
  private static final Set<String> READ_ONLY_OPENSPEC =
      Set.of(
          "list",
          "show",
          "validate",
          "status",
          "view",
          "instructions",
          "context",
          "doctor",
          "schemas",
          "templates",
          "version",
          "help",
          "completion");
  private static final Set<String> HELP = Set.of("-h", "--help");
  private static final Set<String> FIND_WRITES =
      Set.of("-exec", "-execdir", "-ok", "-okdir", "-fprint", "-fprint0", "-fprintf", "-fls");
  private static final Set<String> FIND_FILTERS =
      Set.of("-name", "-iname", "-path", "-ipath", "-wholename", "-regex", "-iregex");
  private static final Set<String> IN_PLACE_EDITORS = Set.of("sed", "perl", "ruby");
  private static final Set<String> AWKS = Set.of("awk", "gawk", "mawk");
  private static final Pattern SED_WRITE =
      Pattern.compile("(?:^|[;}\\n]|/[gpiImMe0-9]*)\\s*[wW]\\s+([^;}\\n]+)");
  private static final Pattern AWK_WRITE = Pattern.compile(">>?\\s*\"([^\"]+)\"");
  private static final Set<String> OPTIONS_WITH_VALUE = Set.of("--store", "--schema", "-C");
  private static final String ARCHIVE = "archive";
  private static final String INTEGRATE = "integrate";
  private static final String MOVE = "mv";
  private static final String DATA_OUTPUT = "of=";

  private ShellToolRules() {}

  /** Classifies a tool this class knows about and returns whether it did. */
  static boolean classify(
      final String name, final List<ShellWord> arguments, final ShellFindings findings) {
    switch (name) {
      case "git" -> git(arguments, findings);
      case "openspec" -> openspec(arguments, findings);
      case "sentinel" -> {
        if (INTEGRATE.equals(firstOperand(arguments))) {
          ShellCommandRules.all(arguments, GuardOperation.WRITE, findings);
        }
      }
      case "find" -> find(arguments, findings);
      case "dd" ->
          arguments.stream()
              .filter(argument -> argument.text().startsWith(DATA_OUTPUT))
              .forEach(
                  argument ->
                      findings.target(
                          GuardOperation.WRITE,
                          argument.withText(argument.text().substring(DATA_OUTPUT.length()))));
      default -> {
        return editor(name, arguments, findings);
      }
    }
    return true;
  }

  private static boolean editor(
      final String name, final List<ShellWord> arguments, final ShellFindings findings) {
    if (IN_PLACE_EDITORS.contains(name)) {
      final boolean inPlace =
          arguments.stream().map(ShellWord::text).anyMatch(ShellToolRules::inPlace);
      final boolean sedWrite = "sed".equals(name) && programWrites(arguments, SED_WRITE, findings);
      if (inPlace || sedWrite || !"sed".equals(name)) {
        ShellCommandRules.all(arguments, GuardOperation.WRITE, findings);
      }
      return true;
    }
    if (AWKS.contains(name)) {
      final boolean writes =
          arguments.stream()
              .map(ShellWord::text)
              .anyMatch(
                  text ->
                      "inplace".equals(text)
                          || text.contains(">")
                          || text.contains("system(")
                          || text.contains("| "));
      if (writes) {
        programWrites(arguments, AWK_WRITE, findings);
        ShellCommandRules.all(arguments, GuardOperation.WRITE, findings);
      }
      return true;
    }
    return false;
  }

  /** Records the files a sed or awk program writes and returns whether it writes any. */
  private static boolean programWrites(
      final List<ShellWord> arguments, final Pattern write, final ShellFindings findings) {
    boolean writes = false;
    for (final ShellWord argument : arguments) {
      final Matcher matcher = write.matcher(argument.text());
      while (matcher.find()) {
        writes = true;
        findings.target(GuardOperation.WRITE, argument.withText(matcher.group(1).strip()));
      }
    }
    return writes;
  }

  private static boolean inPlace(final String option) {
    return option.startsWith("--in-place")
        || option.startsWith("-") && !option.startsWith("--") && option.indexOf('i') > 0;
  }

  private static void git(final List<ShellWord> arguments, final ShellFindings findings) {
    int index = 0;
    while (index < arguments.size() && arguments.get(index).text().startsWith("-")) {
      final String option = arguments.get(index).text();
      index += GIT_OPTIONS_WITH_VALUE.contains(option) ? 2 : 1;
    }
    if (index >= arguments.size()) {
      return;
    }
    final String command = arguments.get(index).text();
    final List<ShellWord> rest = arguments.subList(index + 1, arguments.size());
    if (READ_ONLY_GIT.contains(command)) {
      return;
    }
    if (MOVE.equals(command)) {
      ShellCommandRules.move(rest, findings);
    } else {
      ShellCommandRules.all(
          rest,
          DELETING_GIT.contains(command) ? GuardOperation.DELETE : GuardOperation.WRITE,
          findings);
    }
  }

  private static void openspec(final List<ShellWord> arguments, final ShellFindings findings) {
    final String command = firstOperand(arguments);
    final boolean help = arguments.stream().anyMatch(argument -> HELP.contains(argument.text()));
    if (ARCHIVE.equals(command)) {
      if (!help) {
        findings.archiveCommand();
      }
    } else if (command != null && !READ_ONLY_OPENSPEC.contains(command) && !help) {
      ShellCommandRules.all(arguments, GuardOperation.WRITE, findings);
    }
  }

  private static void find(final List<ShellWord> arguments, final ShellFindings findings) {
    final List<ShellWord> roots = new ArrayList<>();
    int index = 0;
    while (index < arguments.size() && !isExpression(arguments.get(index).text())) {
      roots.add(arguments.get(index));
      index++;
    }
    if (roots.isEmpty()) {
      roots.add(new ShellWord(".", false, false));
    }
    final List<String> expression =
        arguments.subList(index, arguments.size()).stream().map(ShellWord::text).toList();
    final boolean deletes = expression.contains("-delete");
    if (!deletes && expression.stream().noneMatch(FIND_WRITES::contains)) {
      return;
    }
    final GuardOperation operation =
        deletes && expression.stream().noneMatch(FIND_FILTERS::contains)
            ? GuardOperation.DELETE
            : GuardOperation.WRITE;
    roots.forEach(root -> findings.target(operation, root));
    findOutputs(arguments.subList(index, arguments.size()), findings);
  }

  /** Records the files named by {@code find} actions that write their own output. */
  private static void findOutputs(final List<ShellWord> expression, final ShellFindings findings) {
    for (int position = 0; position < expression.size() - 1; position++) {
      final String action = expression.get(position).text();
      if (action.startsWith("-fprint") || "-fls".equals(action)) {
        findings.target(GuardOperation.WRITE, expression.get(position + 1));
      }
    }
  }

  private static boolean isExpression(final String text) {
    return text.startsWith("-") || "(".equals(text) || "!".equals(text);
  }

  /** Returns the first argument that is neither an option nor an option's value. */
  private static String firstOperand(final List<ShellWord> arguments) {
    int index = 0;
    while (index < arguments.size()) {
      final String text = arguments.get(index).text();
      if (!text.startsWith("-")) {
        return text;
      }
      index += OPTIONS_WITH_VALUE.contains(text) ? 2 : 1;
    }
    return null;
  }
}
