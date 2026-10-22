package dev.ward.domain.spec;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rules for text editors and awk programs that can rewrite files in place. */
final class ShellEditorRules {
  private static final Set<String> IN_PLACE_EDITORS = Set.of("sed", "perl", "ruby");
  private static final Set<String> AWKS = Set.of("awk", "gawk", "mawk");
  private static final Pattern SED_WRITE =
      Pattern.compile("(?:^|[;}\\n]|/[gpiImMe0-9]*)\\s*[wW]\\s+([^;}\\n]+)");
  private static final Pattern AWK_WRITE = Pattern.compile(">>?\\s*\"([^\"]+)\"");

  private ShellEditorRules() {}

  /**
   * Records the write targets of an in-place editor or an awk program and returns whether {@code
   * name} is one of those editors.
   */
  static boolean classify(
      final String name, final List<ShellWord> arguments, final ShellFindings findings) {
    if (IN_PLACE_EDITORS.contains(name)) {
      inPlaceEditor(name, arguments, findings);
      return true;
    }
    if (AWKS.contains(name)) {
      awk(arguments, findings);
      return true;
    }
    return false;
  }

  private static void inPlaceEditor(
      final String name, final List<ShellWord> arguments, final ShellFindings findings) {
    final boolean inPlace =
        arguments.stream().map(ShellWord::text).anyMatch(ShellEditorRules::inPlace);
    final boolean sedWrite = "sed".equals(name) && programWrites(arguments, SED_WRITE, findings);
    if (inPlace || sedWrite || !"sed".equals(name)) {
      ShellCommandRules.all(arguments, GuardOperation.WRITE, findings);
    }
  }

  private static void awk(final List<ShellWord> arguments, final ShellFindings findings) {
    final boolean writes =
        arguments.stream().map(ShellWord::text).anyMatch(ShellEditorRules::awkProgramWrites);
    if (writes) {
      programWrites(arguments, AWK_WRITE, findings);
      ShellCommandRules.all(arguments, GuardOperation.WRITE, findings);
    }
  }

  private static boolean awkProgramWrites(final String text) {
    return "inplace".equals(text)
        || text.contains(">")
        || text.contains("system(")
        || text.contains("| ");
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
}
