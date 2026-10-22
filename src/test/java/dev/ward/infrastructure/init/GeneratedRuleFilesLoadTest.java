package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.ConfigurationLoader;
import com.puppycrawl.tools.checkstyle.PropertiesExpander;
import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.AuditListener;
import dev.ward.ProjectFixtures;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.xml.parsers.DocumentBuilderFactory;
import net.sourceforge.pmd.lang.rule.RuleSet;
import net.sourceforge.pmd.lang.rule.RuleSetLoader;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.w3c.dom.Element;

/**
 * Loads the rule files init writes with the real tools. The CLI fixtures use a stub {@code mvnw},
 * so without this a file the tools reject passes every other test.
 */
class GeneratedRuleFilesLoadTest {
  @TempDir Path root;

  @ParameterizedTest
  @EnumSource(QualityPreset.class)
  void checkstyleLoadsTheConfigurationAndAppliesItsProperties(final QualityPreset preset)
      throws Exception {
    final Path config = generate(preset).resolve("checkstyle.xml");
    final Path source = root.resolve("Sample.java");
    Files.writeString(
        source,
        """
        /** Sample. */
        public class Sample {
          private final int value;

          /** Creates a sample. */
          public Sample(final int value) {
            this.value = value;
          }

          /** Adds six numbers. */
          public int add(int a, int b, int c, int d, int e, int f) {
            return value + a + b + c + d + e + f;
          }

          /** Runs a task. */
          public static void run(final Runnable task) {
            try {
              task.run();
            } catch (Throwable failure) {
              throw new IllegalStateException(failure);
            }
          }
        }
        """);

    final List<String> violations = new ArrayList<>();
    final Checker checker = new Checker();
    checker.setModuleClassLoader(Thread.currentThread().getContextClassLoader());
    checker.configure(
        ConfigurationLoader.loadConfiguration(
            config.toString(), new PropertiesExpander(new Properties())));
    checker.addListener(new Recording(violations));
    try {
      checker.process(List.<File>of(source.toFile()));
    } finally {
      checker.destroy();
    }

    assertThat(Files.readString(config)).startsWith("<!-- managed-by: ward preset=");
    assertThat(violations).anySatisfy(violation -> assertThat(violation).contains("IllegalCatch"));
    if (preset == QualityPreset.STRICT) {
      // ParameterNumber.max=5 applies; HiddenField.ignoreConstructorParameter=true applies.
      assertThat(violations)
          .anySatisfy(violation -> assertThat(violation).contains("ParameterNumber"))
          .noneSatisfy(violation -> assertThat(violation).contains("HiddenField"));
    } else {
      assertThat(violations).hasSize(1);
    }
  }

  @ParameterizedTest
  @EnumSource(QualityPreset.class)
  void pmdResolvesEveryRuleReference(final QualityPreset preset) {
    final RuleSet rules =
        new RuleSetLoader()
            .warnDeprecated(false)
            .loadFromResource(generate(preset).resolve("pmd-ruleset.xml").toString());

    assertThat(rules.getRules()).isNotEmpty();
  }

  @ParameterizedTest
  @EnumSource(QualityPreset.class)
  void enforcerRulesUseTheExternalRulesDocumentShape(final QualityPreset preset) throws Exception {
    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    final Element document =
        factory
            .newDocumentBuilder()
            .parse(generate(preset).resolve("enforcer-rules.xml").toFile())
            .getDocumentElement();

    assertThat(document.getTagName()).isEqualTo("enforcer");
    assertThat(document.getElementsByTagName("rules").getLength()).isOne();
    assertThat(document.getElementsByTagName("dependencyConvergence").getLength()).isOne();
  }

  private Path generate(final QualityPreset preset) {
    ProjectFixtures.withPom(root, ProjectFixtures.PLAIN_POM);
    new PresetRuleFileWriter()
        .apply(
            new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE),
            preset,
            List.of(gate("checkstyle"), gate("pmd"), gate("enforcer")));
    return root.resolve("config");
  }

  private static InitGateOption gate(final String id) {
    return new InitGateOption(id, id, List.of("mvn", id), true, "available");
  }

  /** Collects each reported violation as {@code CheckName: message}. */
  private record Recording(List<String> violations) implements AuditListener {
    @Override
    public void addError(final AuditEvent event) {
      final String source = event.getSourceName();
      violations.add(source.substring(source.lastIndexOf('.') + 1) + ": " + event.getMessage());
    }

    @Override
    public void addException(final AuditEvent event, final Throwable throwable) {
      violations.add("exception: " + throwable.getMessage());
    }

    @Override
    public void auditStarted(final AuditEvent event) {
      // Only violations matter.
    }

    @Override
    public void auditFinished(final AuditEvent event) {
      // Only violations matter.
    }

    @Override
    public void fileStarted(final AuditEvent event) {
      // Only violations matter.
    }

    @Override
    public void fileFinished(final AuditEvent event) {
      // Only violations matter.
    }
  }
}
