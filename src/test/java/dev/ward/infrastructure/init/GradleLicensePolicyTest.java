package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.QualityPreset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The plugin matches each rule as a whole-string Java regular expression. */
class GradleLicensePolicyTest {
  private static final Pattern RULE = Pattern.compile("\"moduleLicense\": \"([^\"]*)\"");

  @Test
  void defaultPolicyDeniesGplAndAgplInAnySpelling() {
    final List<String> rules = rules(QualityPreset.STANDARD);

    assertThat(allowed(rules, "GPL-3.0")).isFalse();
    assertThat(allowed(rules, "gnu general public license v2.0")).isFalse();
    assertThat(allowed(rules, "GNU Affero General Public License, Version 3")).isFalse();
    assertThat(allowed(rules, "AGPLv3")).isFalse();
    assertThat(
            allowed(
                rules, "The GNU General Public License, v2 with Universal FOSS Exception, v1.0"))
        .isFalse();
  }

  @Test
  void defaultPolicyAllowsLgplClasspathExceptionAndUnknownLicenses() {
    final List<String> rules = rules(QualityPreset.STANDARD);

    assertThat(allowed(rules, "GNU Lesser General Public License")).isTrue();
    assertThat(allowed(rules, "LGPL-2.1-or-later")).isTrue();
    assertThat(allowed(rules, "GPL2 w/ CPE")).isTrue();
    assertThat(
            allowed(
                rules, "GNU General Public License, version 2 with the GNU Classpath Exception"))
        .isTrue();
    assertThat(allowed(rules, "Mozilla Public License 2.0")).isTrue();
    assertThat(allowed(rules, "")).isTrue();
  }

  @Test
  void strictPolicyAllowsOnlyPermissiveLicensesAndEpl() {
    final List<String> rules = rules(QualityPreset.STRICT);

    assertThat(allowed(rules, "The Apache Software License, Version 2.0")).isTrue();
    assertThat(allowed(rules, "MIT")).isTrue();
    assertThat(allowed(rules, "The MIT License (MIT)")).isTrue();
    assertThat(allowed(rules, "BSD-3-Clause")).isTrue();
    assertThat(allowed(rules, "Eclipse Public License - v 2.0")).isTrue();
    assertThat(allowed(rules, "EPL-1.0")).isTrue();
    assertThat(allowed(rules, "LGPL-2.1-or-later")).isFalse();
    assertThat(allowed(rules, "Mozilla Public License 2.0")).isFalse();
    assertThat(allowed(rules, "GPL-3.0")).isFalse();
    assertThat(allowed(rules, "Permitted")).isFalse();
    assertThat(allowed(rules, "")).isFalse();
  }

  @Test
  void kotlinAndGroovyScriptsUseRawMultilineStrings() {
    assertThat(GradleLicensePolicy.block(true, null))
        .startsWith("licenseReport {\n    allowedLicensesFile = resources.text.fromString(\"\"\"\n")
        .endsWith("\n\"\"\")\n}\n")
        .doesNotContain("$", "\\");
    assertThat(GradleLicensePolicy.block(false, QualityPreset.STRICT))
        .contains("fromString('''\n{\"allowedLicenses\": [\n")
        .endsWith("\n''')\n}\n")
        .doesNotContain("$", "\\");
  }

  private static List<String> rules(final QualityPreset preset) {
    final Matcher matcher = RULE.matcher(GradleLicensePolicy.block(false, preset));
    return matcher.results().map(result -> result.group(1)).toList();
  }

  private static boolean allowed(final List<String> rules, final String license) {
    return rules.stream().anyMatch(rule -> Pattern.compile(rule).matcher(license).matches());
  }
}
