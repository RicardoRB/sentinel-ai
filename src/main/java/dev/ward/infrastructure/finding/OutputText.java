package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.GateResult;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Captured gate output as plain lines, without ANSI colour sequences. */
final class OutputText {
  private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");

  private OutputText() {}

  static List<String> lines(final GateResult result) {
    return Stream.of(result.stdout(), result.stderr())
        .filter(text -> text != null && !text.isEmpty())
        .flatMap(String::lines)
        .map(line -> ANSI.matcher(line).replaceAll(""))
        .toList();
  }
}
