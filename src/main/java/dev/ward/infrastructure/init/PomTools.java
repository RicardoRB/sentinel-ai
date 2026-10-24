package dev.ward.infrastructure.init;

import dev.ward.domain.init.QualityPreset;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;

/** The Maven tools init can add to pom.xml, one row per gate, with their default declarations. */
final class PomTools {
  private static final String CHECKSTYLE = "maven-checkstyle-plugin";
  private static final String PMD = "maven-pmd-plugin";
  private static final String SPOTBUGS = "spotbugs-maven-plugin";
  private static final String SONAR = "sonar-maven-plugin";
  private static final String ARCHUNIT = "archunit-junit5";
  private static final String JACOCO = "jacoco-maven-plugin";
  private static final String DEPENDENCY_CHECK = "dependency-check-maven";
  private static final String PIT = "pitest-maven";
  private static final String ENFORCER = "maven-enforcer-plugin";
  private static final String SPOTLESS = "spotless-maven-plugin";
  private static final String LICENSE = "license-maven-plugin";
  private static final String JAPICMP = "japicmp-maven-plugin";

  private static final String SPOTLESS_PLUGIN =
      """
              <plugin>
                <groupId>com.diffplug.spotless</groupId>
                <artifactId>spotless-maven-plugin</artifactId>
                <version>2.44.4</version>
                <configuration>
                  <java>
                    <googleJavaFormat/>
                  </java>
                </configuration>
              </plugin>
            """;
  private static final String LICENSE_PLUGIN =
      """
              <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>license-maven-plugin</artifactId>
                <version>2.5.0</version>
                <configuration>
                  <failOnBlacklist>true</failOnBlacklist>
                  <excludedLicenses>
                    <excludedLicense>GNU General Public License (GPL)</excludedLicense>
                    <excludedLicense>GNU Affero General Public License (AGPL)</excludedLicense>
                  </excludedLicenses>
                </configuration>
              </plugin>
            """;
  private static final String JAPICMP_PLUGIN =
      """
              <plugin>
                <groupId>com.github.siom79.japicmp</groupId>
                <artifactId>japicmp-maven-plugin</artifactId>
                <version>0.23.1</version>
                <configuration>
                  <oldVersion>
                    <dependency>
                      <groupId>${project.groupId}</groupId>
                      <artifactId>${project.artifactId}</artifactId>
                      <version>RELEASE</version>
                      <type>jar</type>
                    </dependency>
                  </oldVersion>
                  <newVersion><file><path>${project.build.directory}/${project.build.finalName}.jar</path></file></newVersion>
                  <parameter>
                    <breakBuildOnBinaryIncompatibleModifications>true</breakBuildOnBinaryIncompatibleModifications>
                    <includes><include>${project.groupId}.*</include></includes>
                  </parameter>
                </configuration>
              </plugin>
            """;

  private static final String CHECKSTYLE_PLUGIN =
      """
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-checkstyle-plugin</artifactId>
                <version>3.6.0</version>
              </plugin>
            """;
  private static final String SPOTBUGS_PLUGIN =
      """
              <plugin>
                <groupId>com.github.spotbugs</groupId>
                <artifactId>spotbugs-maven-plugin</artifactId>
                <version>4.9.7.0</version>
              </plugin>
            """;
  private static final String SONAR_PLUGIN =
      """
              <plugin>
                <groupId>org.sonarsource.scanner.maven</groupId>
                <artifactId>sonar-maven-plugin</artifactId>
                <version>5.1.0.4751</version>
              </plugin>
            """;
  private static final String ARCHUNIT_DEPENDENCY =
      """
              <dependency>
                <groupId>com.tngtech.archunit</groupId>
                <artifactId>archunit-junit5</artifactId>
                <version>1.3.0</version>
                <scope>test</scope>
              </dependency>
            """;
  private static final String JACOCO_PLUGIN =
      """
              <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>0.8.13</version>
                <configuration>
                  <rules>
                    <rule>
                      <element>BUNDLE</element>
                      <limits>
                        <limit>
                          <counter>LINE</counter>
                          <value>COVEREDRATIO</value>
                          <minimum>0.80</minimum>
                        </limit>
                      </limits>
                    </rule>
                  </rules>
                </configuration>
              </plugin>
            """;
  private static final String DEPENDENCY_CHECK_PLUGIN =
      """
              <plugin>
                <groupId>org.owasp</groupId>
                <artifactId>dependency-check-maven</artifactId>
                <version>12.1.0</version>
                 <configuration>
                   <failBuildOnCVSS>7</failBuildOnCVSS>
                   <nvdApiKeyEnvironmentVariable>NVD_API_KEY</nvdApiKeyEnvironmentVariable>
                   <ossindexAnalyzerEnabled>false</ossindexAnalyzerEnabled>
                   <ossIndexServerId>ossindex</ossIndexServerId>
                 </configuration>
              </plugin>
            """;
  static final String PIT_PLUGIN =
      """
              <plugin>
                <groupId>org.pitest</groupId>
                <artifactId>pitest-maven</artifactId>
                <version>1.30.1</version>
                <dependencies>
                  <dependency>
                    <groupId>org.pitest</groupId>
                    <artifactId>pitest-junit5-plugin</artifactId>
                    <version>1.2.3</version>
                  </dependency>
                </dependencies>
              </plugin>
            """;
  private static final String ENFORCER_PLUGIN =
      """
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-enforcer-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                  <rules>
                    <dependencyConvergence/>
                  </rules>
                  <fail>true</fail>
                </configuration>
              </plugin>
            """;
  private static final String PMD_PLUGIN =
      """
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-pmd-plugin</artifactId>
                <version>3.26.0</version>
                <configuration><analysisCache>true</analysisCache></configuration>
              </plugin>
            """;

  /** Tools in the order they are added to pom.xml. */
  static final List<Tool> TOOLS =
      List.of(
          Tool.plugin(
              "checkstyle",
              CHECKSTYLE,
              CHECKSTYLE_PLUGIN,
              preset -> PresetPluginSnippets.checkstyle()),
          Tool.plugin("pmd", PMD, PMD_PLUGIN, preset -> PresetPluginSnippets.pmd()),
          Tool.plugin("spotbugs", SPOTBUGS, SPOTBUGS_PLUGIN, PresetPluginSnippets::spotbugs),
          Tool.plugin("sonar", SONAR, SONAR_PLUGIN),
          Tool.plugin("coverage", JACOCO, JACOCO_PLUGIN, PresetPluginSnippets::jacoco),
          Tool.plugin("dependency-check", DEPENDENCY_CHECK, DEPENDENCY_CHECK_PLUGIN),
          new Tool("archunit", ARCHUNIT, ARCHUNIT_DEPENDENCY, null, true),
          Tool.plugin("mutation", PIT, PIT_PLUGIN, PresetPluginSnippets::pit),
          Tool.plugin(
              "enforcer", ENFORCER, ENFORCER_PLUGIN, preset -> PresetPluginSnippets.enforcer()),
          Tool.plugin("format", SPOTLESS, SPOTLESS_PLUGIN),
          Tool.plugin("license", LICENSE, LICENSE_PLUGIN),
          Tool.plugin("api-compat", JAPICMP, JAPICMP_PLUGIN));

  private PomTools() {}

  /**
   * A Maven tool added for a selected gate. Its preset snippet, when present, replaces the default
   * snippet under a quality preset.
   */
  record Tool(
      String gate,
      String artifact,
      String snippet,
      Function<QualityPreset, String> presetSnippet,
      boolean dependency) {
    private static final String VERSION_START = "<version>";

    static Tool plugin(final String gate, final String artifact, final String snippet) {
      return new Tool(gate, artifact, snippet, null, false);
    }

    static Tool plugin(
        final String gate,
        final String artifact,
        final String snippet,
        final Function<QualityPreset, String> presetSnippet) {
      return new Tool(gate, artifact, snippet, presetSnippet, false);
    }

    String addTo(final String pom, final QualityPreset preset, final String selectedVersion) {
      final String chosen =
          preset == null || presetSnippet == null ? snippet : presetSnippet.apply(preset);
      final String versioned =
          selectedVersion == null
              ? chosen
              : chosen.replaceFirst(
                  "<version>[^<]+</version>",
                  Matcher.quoteReplacement("<version>" + selectedVersion + "</version>"));
      return dependency ? PomXml.addDependency(pom, versioned) : PomXml.addPlugin(pom, versioned);
    }

    /** The version this tool's declaration pins, preferring a compatibility-selected version. */
    String version(final QualityPreset preset, final String selectedVersion) {
      if (selectedVersion != null) {
        return selectedVersion;
      }
      final String chosen =
          preset == null || presetSnippet == null ? snippet : presetSnippet.apply(preset);
      final int start = chosen.indexOf(VERSION_START);
      return start < 0
          ? ""
          : chosen.substring(start + VERSION_START.length(), chosen.indexOf("</version>", start));
    }
  }
}
