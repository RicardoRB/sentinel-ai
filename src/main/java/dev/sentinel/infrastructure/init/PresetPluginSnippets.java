package dev.sentinel.infrastructure.init;

import dev.sentinel.domain.init.QualityPreset;

/** Plugin declarations tuned by a quality preset, used instead of the default declarations. */
final class PresetPluginSnippets {
  private PresetPluginSnippets() {}

  static String pmd() {
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
                <analysisCache>true</analysisCache>
              </configuration>
            </plugin>
          """;
  }

  static String checkstyle() {
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

  static String spotbugs(final QualityPreset preset) {
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

  static String jacoco(final QualityPreset preset) {
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

  static String enforcer() {
    return """
            <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-enforcer-plugin</artifactId><version>3.5.0</version>
              <configuration><rules><externalRules><location>${project.basedir}/config/enforcer-rules.xml</location></externalRules></rules><fail>true</fail></configuration>
            </plugin>
          """;
  }

  static String pit(final QualityPreset preset) {
    if (preset.rules().mutationThreshold() == null) {
      return PomToolConfigurator.PIT_PLUGIN;
    }
    return """
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
              <configuration>
                <mutationThreshold>THRESHOLD</mutationThreshold>
              </configuration>
            </plugin>
          """
        .replace("THRESHOLD", String.valueOf(preset.rules().mutationThreshold()));
  }
}
