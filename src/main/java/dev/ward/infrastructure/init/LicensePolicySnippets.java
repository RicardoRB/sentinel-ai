package dev.ward.infrastructure.init;

import dev.ward.domain.init.QualityPreset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * License Maven Plugin policies. The plugin matches license names exactly and case-sensitively, so
 * both policies merge common spellings into one name. Most presets deny GPL and AGPL, which keeps
 * unknown projects building; the strict preset allows only listed licenses and fails on the rest.
 */
final class LicensePolicySnippets {
  /** Fails on GPL and AGPL dependencies; licenses it does not recognize pass. */
  static final String DENYLIST =
      """
              <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>license-maven-plugin</artifactId>
                <version>2.5.0</version>
                <configuration>
                  <failOnBlacklist>true</failOnBlacklist>
                  <!-- Names match exactly and POMs spell licenses many ways; common GPL/AGPL spellings
                       merge into the excluded names. Exception variants (Classpath) stay allowed. -->
                  <licenseMerges>
                    <licenseMerge>GNU General Public License (GPL)|GPL|GPLv2|GPLv3</licenseMerge>
                    <licenseMerge>GNU General Public License (GPL)|GPL-2.0</licenseMerge>
                    <licenseMerge>GNU General Public License (GPL)|GPL-2.0-only</licenseMerge>
                    <licenseMerge>GNU General Public License (GPL)|GPL-2.0-or-later</licenseMerge>
                    <licenseMerge>GNU General Public License (GPL)|GPL-3.0</licenseMerge>
                    <licenseMerge>GNU General Public License (GPL)|GPL-3.0-only</licenseMerge>
                    <licenseMerge>GNU General Public License (GPL)|GPL-3.0-or-later</licenseMerge>
                    <licenseMerge>GNU General Public License (GPL)|GNU General Public License v2.0</licenseMerge>
                    <licenseMerge>GNU General Public License (GPL)|GNU General Public License v3.0</licenseMerge>
                    <licenseMerge>GNU General Public License (GPL)|GNU General Public License, version 2</licenseMerge>
                    <licenseMerge>GNU General Public License (GPL)|GNU General Public License, Version 2</licenseMerge>
                    <licenseMerge>GNU General Public License (GPL)|GNU General Public License, version 3</licenseMerge>
                    <licenseMerge>GNU General Public License (GPL)|GNU General Public License, Version 3</licenseMerge>
                    <licenseMerge>GNU General Public License (GPL)|GNU General Public License Version 2</licenseMerge>
                    <licenseMerge>GNU General Public License (GPL)|GNU General Public License Version 3</licenseMerge>
                    <licenseMerge>GNU General Public License (GPL)|The GNU General Public License, Version 2</licenseMerge>
                    <licenseMerge>GNU General Public License (GPL)|The GNU General Public License v3.0</licenseMerge>
                    <licenseMerge>GNU General Public License (GPL)|GNU GPL v2</licenseMerge>
                    <licenseMerge>GNU General Public License (GPL)|GNU GPL v3</licenseMerge>
                    <licenseMerge>GNU General Public License (GPL)|GNU GPLv3</licenseMerge>
                    <licenseMerge>GNU Affero General Public License (AGPL)|AGPL</licenseMerge>
                    <licenseMerge>GNU Affero General Public License (AGPL)|AGPLv3</licenseMerge>
                    <licenseMerge>GNU Affero General Public License (AGPL)|AGPL-3.0</licenseMerge>
                    <licenseMerge>GNU Affero General Public License (AGPL)|AGPL-3.0-only</licenseMerge>
                    <licenseMerge>GNU Affero General Public License (AGPL)|AGPL-3.0-or-later</licenseMerge>
                    <licenseMerge>GNU Affero General Public License (AGPL)|GNU Affero General Public License v3.0</licenseMerge>
                    <licenseMerge>GNU Affero General Public License (AGPL)|GNU Affero General Public License, Version 3</licenseMerge>
                    <licenseMerge>GNU Affero General Public License (AGPL)|GNU Affero General Public License version 3</licenseMerge>
                    <licenseMerge>GNU Affero General Public License (AGPL)|GNU AFFERO GENERAL PUBLIC LICENSE, Version 3</licenseMerge>
                    <licenseMerge>GNU Affero General Public License (AGPL)|The GNU Affero General Public License, Version 3</licenseMerge>
                  </licenseMerges>
                  <excludedLicenses>
                    <excludedLicense>GNU General Public License (GPL)</excludedLicense>
                    <excludedLicense>GNU Affero General Public License (AGPL)</excludedLicense>
                  </excludedLicenses>
                </configuration>
              </plugin>
            """;

  /**
   * Permissive licenses plus EPL, which JUnit uses, keyed by the name the plugin reports. Weak
   * copyleft licenses such as LGPL, MPL, and CDDL need a legal decision, so they are not listed.
   */
  private static final Map<String, List<String>> ALLOWED = allowed();

  private static final String INDENT = "        ";

  private LicensePolicySnippets() {}

  /** Returns the strict preset's allowlist, or the denylist for any other preset. */
  static String forPreset(final QualityPreset preset) {
    return preset == QualityPreset.STRICT ? allowlist() : DENYLIST;
  }

  private static String allowlist() {
    final String merges =
        ALLOWED.entrySet().stream()
            .flatMap(
                entry ->
                    entry.getValue().stream()
                        .map(
                            alias ->
                                INDENT
                                    + "<licenseMerge>"
                                    + entry.getKey()
                                    + "|"
                                    + alias
                                    + "</licenseMerge>\n"))
            .collect(Collectors.joining());
    final String included =
        ALLOWED.keySet().stream()
            .map(name -> INDENT + "<includedLicense>" + name + "</includedLicense>\n")
            .collect(Collectors.joining());
    return """
              <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>license-maven-plugin</artifactId>
                <version>2.5.0</version>
                <configuration>
                  <!-- Allowlist: a license not listed, or a dependency without one, fails the build.
                       Add an approved license to includedLicenses, and its spellings to licenseMerges. -->
                  <failOnBlacklist>true</failOnBlacklist>
                  <licenseMerges>
            """
        + merges
        + """
                  </licenseMerges>
                  <includedLicenses>
            """
        + included
        + """
                  </includedLicenses>
                </configuration>
              </plugin>
            """;
  }

  private static Map<String, List<String>> allowed() {
    final Map<String, List<String>> allowed = new LinkedHashMap<>();
    allowed.put(
        "Apache-2.0",
        List.of(
            "Apache 2.0",
            "Apache 2",
            "Apache License 2.0",
            "Apache License, Version 2.0",
            "Apache License Version 2.0",
            "Apache Software License - Version 2.0",
            "The Apache License, Version 2.0",
            "The Apache Software License, Version 2.0",
            "The Apache Software License, version 2.0",
            "ASF 2.0"));
    allowed.put(
        "MIT", List.of("MIT License", "MIT license", "The MIT License", "The MIT License (MIT)"));
    allowed.put(
        "BSD-3-Clause",
        List.of(
            "BSD",
            "BSD License",
            "BSD-style",
            "The BSD License",
            "BSD 3-Clause",
            "BSD 3-Clause License",
            "BSD-3-Clause License",
            "New BSD License",
            "The New BSD License",
            "Eclipse Distribution License - v 1.0",
            "EDL 1.0"));
    allowed.put(
        "BSD-2-Clause",
        List.of("BSD 2-Clause", "BSD 2-Clause License", "The BSD 2-Clause License"));
    allowed.put("ISC", List.of("ISC License"));
    allowed.put(
        "EPL-1.0",
        List.of(
            "Eclipse Public License 1.0",
            "Eclipse Public License - v 1.0",
            "Eclipse Public License v1.0",
            "Eclipse Public License, Version 1.0"));
    allowed.put(
        "EPL-2.0",
        List.of(
            "Eclipse Public License 2.0",
            "Eclipse Public License - v 2.0",
            "Eclipse Public License v2.0",
            "Eclipse Public License, Version 2.0",
            "EPL 2.0"));
    allowed.put("CC0-1.0", List.of("CC0", "CC0 1.0 Universal"));
    return allowed;
  }
}
