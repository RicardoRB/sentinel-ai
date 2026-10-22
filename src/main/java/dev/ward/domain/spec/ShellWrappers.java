package dev.ward.domain.spec;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strips environment assignments and wrapper commands such as {@code sudo} or {@code npx}. */
final class ShellWrappers {
  private static final Map<String, Set<String>> WRAPPERS =
      Map.ofEntries(
          Map.entry("sudo", Set.of("-u", "-g", "-C", "-h", "-p", "-U")),
          Map.entry("doas", Set.of("-u", "-C")),
          Map.entry("env", Set.of("-u", "-C", "-S", "--unset", "--chdir")),
          Map.entry("command", Set.of()),
          Map.entry("builtin", Set.of()),
          Map.entry("exec", Set.of("-a")),
          Map.entry("nohup", Set.of()),
          Map.entry("time", Set.of("-f", "-o")),
          Map.entry("nice", Set.of("-n")),
          Map.entry("stdbuf", Set.of("-i", "-o", "-e")),
          Map.entry("xargs", Set.of("-I", "-n", "-L", "-P", "-d", "-E", "-s", "-a")),
          Map.entry("npx", Set.of("-p", "--package", "-c", "--call")),
          Map.entry("bunx", Set.of("-p", "--package")));
  private static final Set<String> RUNNERS = Set.of("pnpm", "npm", "yarn", "bun");
  private static final Set<String> RUNNER_COMMANDS = Set.of("exec", "dlx", "x");
  private static final String END_OF_OPTIONS = "--";
  private static final String TIMEOUT = "timeout";

  private ShellWrappers() {}

  /** Returns the words starting at the command that actually runs. */
  static List<ShellWord> unwrap(final List<ShellWord> words) {
    int index = 0;
    int previous = -1;
    while (index < words.size() && index != previous) {
      previous = index;
      while (index < words.size() && isAssignment(words.get(index).text())) {
        index++;
      }
      if (index < words.size()) {
        index = skipWrapper(words, index);
      }
    }
    return words.subList(index, words.size());
  }

  /** Returns the command name without directories, package scope, or version suffix. */
  static String executable(final String word) {
    final String name = word.substring(word.lastIndexOf('/') + 1);
    final int version = name.indexOf('@', 1);
    return version > 0 ? name.substring(0, version) : name;
  }

  private static int skipWrapper(final List<ShellWord> words, final int index) {
    final String text = words.get(index).text();
    if (END_OF_OPTIONS.equals(text)) {
      return index + 1;
    }
    final String name = executable(text);
    if (WRAPPERS.containsKey(name)) {
      return skipOptions(words, index + 1, WRAPPERS.get(name));
    }
    if (TIMEOUT.equals(name)) {
      final int duration = skipOptions(words, index + 1, Set.of("-s", "-k"));
      return Math.min(duration + 1, words.size());
    }
    if (RUNNERS.contains(name)
        && index + 1 < words.size()
        && RUNNER_COMMANDS.contains(words.get(index + 1).text())) {
      return skipOptions(words, index + 2, Set.of());
    }
    return index;
  }

  private static int skipOptions(
      final List<ShellWord> words, final int start, final Set<String> withValue) {
    int index = start;
    while (index < words.size() && words.get(index).text().startsWith("-")) {
      final String option = words.get(index).text();
      index += withValue.contains(option) ? 2 : 1;
      if (END_OF_OPTIONS.equals(option)) {
        break;
      }
    }
    return Math.min(index, words.size());
  }

  private static boolean isAssignment(final String word) {
    return word.matches("[A-Za-z_][A-Za-z0-9_]*=.*");
  }
}
