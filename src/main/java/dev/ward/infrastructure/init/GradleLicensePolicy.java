package dev.ward.infrastructure.init;

import dev.ward.domain.init.QualityPreset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The {@code licenseReport} block for the dependency-license-report plugin's {@code checkLicense}
 * task. The plugin matches each rule as a whole-string regular expression, so the rules ignore case
 * and spelling. A dependency passes when one of its licenses matches a rule.
 */
final class GradleLicensePolicy {
  /**
   * Allows every license except GPL and AGPL; LGPL and the Classpath Exception stay allowed, and so
   * does a dependency without a license. Other GPL exceptions, such as the Universal FOSS
   * Exception, only cover open-source use, so they are denied.
   */
  private static final List<String> DENY_GPL =
      List.of(
          "(?is)(?!(?!.*(?:lesser|library|lgpl|classpath|cpe))"
              + ".*(?:(?:^|[^a-z])a?gpl|general public license|affero)).*",
          "");

  /**
   * The strict preset allows only permissive licenses plus EPL, which JUnit uses. A dependency
   * without a license fails.
   */
  private static final List<String> ALLOW_PERMISSIVE =
      List.of(
          "(?is).*apache.*2.*",
          "(?is)(?:.*[^a-z])?mit(?:[^a-z].*)?",
          "(?is).*bsd.*",
          "(?is).*eclipse distribution license.*",
          "(?is)(?:the )?isc(?: license)?",
          "(?is)(?:.*eclipse public license.*|(?:.*[^a-z])?epl(?:[^a-z].*)?)",
          "(?is).*(?:cc0|public domain).*");

  private GradleLicensePolicy() {}

  /** Returns the block for {@code preset}; a {@code null} preset gets the GPL/AGPL denylist. */
  static String block(final boolean kotlin, final QualityPreset preset) {
    final String rules =
        (preset == QualityPreset.STRICT ? ALLOW_PERMISSIVE : DENY_GPL)
            .stream()
                .map(rule -> "    {\"moduleLicense\": \"" + rule + "\"}")
                .collect(Collectors.joining(",\n", "{\"allowedLicenses\": [\n", "\n]}"));
    final String quote = kotlin ? "\"\"\"" : "'''";
    return "licenseReport {\n"
        + "    allowedLicensesFile = resources.text.fromString("
        + quote
        + "\n"
        + rules
        + "\n"
        + quote
        + ")\n"
        + "}\n";
  }
}
