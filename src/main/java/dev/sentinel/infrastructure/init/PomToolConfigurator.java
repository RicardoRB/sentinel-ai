package dev.sentinel.infrastructure.init;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.BuildToolConfiguration;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.PomChange;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import javax.inject.Inject;

/** Adds only missing, pinned Maven tool declarations required by selected init gates. */
public final class PomToolConfigurator implements BuildToolConfiguration {
  @Inject
  public PomToolConfigurator() {}

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
                  <parameter>
                    <breakBuildOnBinaryIncompatibleModifications>true</breakBuildOnBinaryIncompatibleModifications>
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
                </configuration>
              </plugin>
            """;
  private static final String PIT_PLUGIN =
      """
              <plugin>
                <groupId>org.pitest</groupId>
                <artifactId>pitest-maven</artifactId>
                <version>1.17.4</version>
                <dependencies>
                  <dependency>
                    <groupId>org.pitest</groupId>
                    <artifactId>pitest-junit5-plugin</artifactId>
                    <version>1.2.1</version>
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
              </plugin>
            """;

  @Override
  public PomChange apply(final Project project, final List<InitGateOption> gates) {
    return apply(project, gates, null);
  }

  @Override
  public PomChange apply(
      final Project project, final List<InitGateOption> gates, final QualityPreset preset) {
    final Path pom = project.root().resolve("pom.xml");
    try {
      final String original = Files.readString(pom);
      String updated = original;
      final List<String> tools = new ArrayList<>();
      final List<String> warnings = new ArrayList<>();
      final boolean needsArchUnit = gates.stream().anyMatch(gate -> "archunit".equals(gate.id()));
      if (gates.stream().anyMatch(gate -> "checkstyle".equals(gate.id()))
          && !containsArtifact(updated, CHECKSTYLE)) {
        updated = addPlugin(updated, preset == null ? CHECKSTYLE_PLUGIN : checkstylePlugin());
        tools.add(CHECKSTYLE);
      } else if (preset != null
          && gates.stream().anyMatch(gate -> "checkstyle".equals(gate.id()))) {
        warnings.add(CHECKSTYLE + " already declared; " + preset.id() + " settings not applied");
      }
      if (gates.stream().anyMatch(gate -> "pmd".equals(gate.id()))
          && !containsArtifact(updated, PMD)) {
        updated = addPlugin(updated, preset == null ? PMD_PLUGIN : pmdPlugin());
        tools.add(PMD);
      } else if (preset != null && gates.stream().anyMatch(gate -> "pmd".equals(gate.id()))) {
        warnings.add(PMD + " already declared; " + preset.id() + " settings not applied");
      }
      if (gates.stream().anyMatch(gate -> "spotbugs".equals(gate.id()))
          && !containsArtifact(updated, SPOTBUGS)) {
        updated = addPlugin(updated, preset == null ? SPOTBUGS_PLUGIN : spotbugsPlugin(preset));
        tools.add(SPOTBUGS);
      } else if (preset != null && gates.stream().anyMatch(gate -> "spotbugs".equals(gate.id()))) {
        warnings.add(SPOTBUGS + " already declared; " + preset.id() + " settings not applied");
      }
      if (gates.stream().anyMatch(gate -> "sonar".equals(gate.id()))
          && !containsArtifact(updated, SONAR)) {
        updated = addPlugin(updated, SONAR_PLUGIN);
        tools.add(SONAR);
      }
      if (gates.stream().anyMatch(gate -> "coverage".equals(gate.id()))
          && !containsArtifact(updated, JACOCO)) {
        updated = addPlugin(updated, preset == null ? JACOCO_PLUGIN : jacocoPlugin(preset));
        tools.add(JACOCO);
      } else if (preset != null && gates.stream().anyMatch(gate -> "coverage".equals(gate.id()))) {
        warnings.add(JACOCO + " already declared; " + preset.id() + " settings not applied");
      }
      if (gates.stream().anyMatch(gate -> "dependency-check".equals(gate.id()))
          && !containsArtifact(updated, DEPENDENCY_CHECK)) {
        updated = addPlugin(updated, DEPENDENCY_CHECK_PLUGIN);
        tools.add(DEPENDENCY_CHECK);
      }
      if (needsArchUnit && !containsArtifact(updated, ARCHUNIT)) {
        updated = addDependency(updated, ARCHUNIT_DEPENDENCY);
        tools.add(ARCHUNIT);
      }
      if (gates.stream().anyMatch(gate -> "mutation".equals(gate.id()))
          && !containsArtifact(updated, PIT)) {
        updated = addPlugin(updated, preset == null ? PIT_PLUGIN : pitPlugin(preset));
        tools.add(PIT);
      } else if (preset != null && gates.stream().anyMatch(gate -> "mutation".equals(gate.id()))) {
        warnings.add(PIT + " already declared; " + preset.id() + " settings not applied");
      }
      if (gates.stream().anyMatch(gate -> "enforcer".equals(gate.id()))
          && !containsArtifact(updated, ENFORCER)) {
        updated = addPlugin(updated, preset == null ? ENFORCER_PLUGIN : enforcerPlugin());
        tools.add(ENFORCER);
      } else if (preset != null && gates.stream().anyMatch(gate -> "enforcer".equals(gate.id()))) {
        warnings.add(ENFORCER + " already declared; " + preset.id() + " settings not applied");
      }
      if (gates.stream().anyMatch(gate -> "format".equals(gate.id()))
          && !containsArtifact(updated, SPOTLESS)) {
        updated = addPlugin(updated, SPOTLESS_PLUGIN);
        tools.add(SPOTLESS);
      }
      if (gates.stream().anyMatch(gate -> "license".equals(gate.id()))
          && !containsArtifact(updated, LICENSE)) {
        updated = addPlugin(updated, LICENSE_PLUGIN);
        tools.add(LICENSE);
      }
      if (gates.stream().anyMatch(gate -> "api-compat".equals(gate.id()))
          && !containsArtifact(updated, JAPICMP)) {
        updated = addPlugin(updated, JAPICMP_PLUGIN);
        tools.add(JAPICMP);
      }
      if (!tools.isEmpty()) {
        Files.writeString(
            pom, updated, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
      }
      return new PomChange(pom, original, tools, warnings);
    } catch (IOException e) {
      throw new SentinelException(
          "Could not configure Maven tools in " + pom + ": " + e.getMessage(), e);
    }
  }

  @Override
  public void rollback(final PomChange change) {
    if (change == null || !change.changed()) {
      return;
    }
    try {
      Files.writeString(
          change.file(),
          change.originalContent(),
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
    } catch (IOException e) {
      throw new SentinelException("Could not restore " + change.file() + ": " + e.getMessage(), e);
    }
  }

  private static boolean containsArtifact(final String pom, final String artifactId) {
    return pom.contains("<artifactId>" + artifactId + "</artifactId>");
  }

  private static String addPlugin(final String pom, final String plugin) {
    if (pom.contains("</plugins>")) {
      return pom.replaceFirst("</plugins>", Matcher.quoteReplacement(plugin + "  </plugins>"));
    }
    if (pom.contains("</build>")) {
      return pom.replaceFirst(
          "</build>",
          Matcher.quoteReplacement("  <plugins>\n" + plugin + "  </plugins>\n</build>"));
    }
    if (!pom.contains("</project>")) {
      throw new SentinelException("Cannot safely update pom.xml: missing </project> element.");
    }
    return pom.replaceFirst(
        "</project>",
        Matcher.quoteReplacement(
            "  <build>\n    <plugins>\n" + plugin + "    </plugins>\n  </build>\n</project>"));
  }

  private static String addDependency(final String pom, final String dependency) {
    if (pom.contains("</dependencies>")) {
      return pom.replaceFirst(
          "</dependencies>", Matcher.quoteReplacement(dependency + "  </dependencies>"));
    }
    if (!pom.contains("</project>")) {
      throw new SentinelException("Cannot safely update pom.xml: missing </project> element.");
    }
    return pom.replaceFirst(
        "</project>",
        Matcher.quoteReplacement(
            "  <dependencies>\n" + dependency + "  </dependencies>\n</project>"));
  }

  private static String pmdPlugin() {
    return """
            <plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-pmd-plugin</artifactId>
              <version>3.26.0</version>
              <configuration>
                <rulesets><ruleset>${project.basedir}/config/pmd-ruleset.xml</ruleset></rulesets>
                <failOnViolation>true</failOnViolation>
                <printFailingErrors>true</printFailingErrors>
                <includeTests>false</includeTests>
              </configuration>
            </plugin>
          """;
  }

  private static String checkstylePlugin() {
    return """
            <plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-checkstyle-plugin</artifactId>
              <version>3.6.0</version>
              <dependencies><dependency><groupId>com.puppycrawl.tools</groupId><artifactId>checkstyle</artifactId><version>10.20.2</version></dependency></dependencies>
              <configuration><configLocation>${project.basedir}/config/checkstyle.xml</configLocation><violationSeverity>error</violationSeverity><consoleOutput>true</consoleOutput></configuration>
            </plugin>
          """;
  }

  private static String spotbugsPlugin(final QualityPreset preset) {
    final String exclude =
        preset == QualityPreset.STANDARD
            ? "<excludeFilterFile>${project.basedir}/config/spotbugs-exclude.xml</excludeFilterFile>"
            : "";
    return "<plugin>\n"
        + "  <groupId>com.github.spotbugs</groupId><artifactId>spotbugs-maven-plugin</artifactId><version>4.9.7.0</version>\n"
        + "  <configuration><effort>"
        + preset.rules().spotbugsEffort()
        + "</effort><threshold>"
        + preset.rules().spotbugsThreshold()
        + "</threshold>"
        + exclude
        + "</configuration>\n"
        + "</plugin>\n";
  }

  private static String jacocoPlugin(final QualityPreset preset) {
    final String limits =
        preset.rules().jacocoMinimums().entrySet().stream()
            .map(
                entry ->
                    "<limit><counter>"
                        + entry.getKey()
                        + "</counter><value>COVEREDRATIO</value><minimum>"
                        + entry.getValue()
                        + "</minimum></limit>")
            .reduce("", String::concat);
    return "<plugin><groupId>org.jacoco</groupId><artifactId>jacoco-maven-plugin</artifactId><version>0.8.13</version>\n"
        + "<executions><execution><goals><goal>prepare-agent</goal></goals></execution><execution><id>report</id><phase>test</phase><goals><goal>report</goal></goals></execution></executions>\n"
        + "<configuration><rules><rule><element>BUNDLE</element><limits>"
        + limits
        + "</limits></rule></rules></configuration></plugin>\n";
  }

  private static String enforcerPlugin() {
    return """
            <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-enforcer-plugin</artifactId><version>3.5.0</version>
              <configuration><rules><externalRules><location>${project.basedir}/config/enforcer-rules.xml</location></externalRules></rules><fail>true</fail></configuration>
            </plugin>
          """;
  }

  private static String pitPlugin(final QualityPreset preset) {
    final String threshold =
        preset.rules().mutationThreshold() == null
            ? ""
            : "<mutationThreshold>" + preset.rules().mutationThreshold() + "</mutationThreshold>";
    return PIT_PLUGIN.replace("</dependencies>", "</dependencies>\n                  " + threshold);
  }
}
