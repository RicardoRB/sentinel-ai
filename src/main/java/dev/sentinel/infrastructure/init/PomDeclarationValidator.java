package dev.sentinel.infrastructure.init;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.infrastructure.init.PomElements.Element;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Checks an edited pom.xml before init writes it: the document must be well-formed and every
 * declaration init added must sit in its project-level section with only the child elements the
 * Maven 4.0.0 POM model allows there. Runs in-process, never resolves DTDs or external entities.
 */
final class PomDeclarationValidator {
  private static final Logger LOGGER = Logger.getLogger(PomDeclarationValidator.class.getName());
  private static final String UNSAFE = "Cannot safely update pom.xml: ";

  /** Direct children of {@code <plugin>} in the Maven 4.0.0 POM model. */
  private static final Set<String> PLUGIN_CHILDREN =
      Set.of(
          "groupId",
          "artifactId",
          "version",
          "extensions",
          "executions",
          "dependencies",
          "goals",
          "inherited",
          "configuration");

  /** Direct children of {@code <dependency>} in the Maven 4.0.0 POM model. */
  private static final Set<String> DEPENDENCY_CHILDREN =
      Set.of(
          "groupId",
          "artifactId",
          "version",
          "type",
          "classifier",
          "scope",
          "systemPath",
          "exclusions",
          "optional");

  private PomDeclarationValidator() {}

  /** Throws {@link SentinelException} naming the offending tool when the edited pom is unsafe. */
  static void validate(final String pom, final List<Declaration> added) {
    requireWellFormed(pom, unsafe(added));
    for (final Declaration declaration : added) {
      requireValid(pom, declaration);
    }
    LOGGER.log(Level.FINE, () -> "event=pom-validation status=passed declarations=" + added.size());
  }

  private static void requireWellFormed(final String pom, final String unsafe) {
    try {
      final SAXParserFactory factory = SAXParserFactory.newInstance();
      factory.setNamespaceAware(false);
      factory.setValidating(false);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      factory.newSAXParser().parse(new InputSource(new StringReader(pom)), new DefaultHandler());
    } catch (SAXParseException e) {
      LOGGER.log(Level.WARNING, "event=pom-validation status=failed reason=not-well-formed");
      throw new SentinelException(unsafe + notWellFormed(pom, e), e);
    } catch (SAXException | ParserConfigurationException | IOException e) {
      LOGGER.log(Level.WARNING, "event=pom-validation status=failed reason=parser-unavailable");
      throw new SentinelException(unsafe + "the XML parser could not check it.", e);
    }
  }

  private static String unsafe(final List<Declaration> added) {
    if (added.isEmpty()) {
      return UNSAFE;
    }
    return "Cannot safely update pom.xml while adding "
        + String.join(", ", added.stream().map(Declaration::artifactId).toList())
        + ": ";
  }

  private static String notWellFormed(final String pom, final SAXParseException e) {
    if (pom.contains("<!DOCTYPE")) {
      return "DOCTYPE declarations are not supported for automated edits;"
          + " add the Maven tool declarations manually.";
    }
    return "the edited content is not well-formed XML (line "
        + e.getLineNumber()
        + ", column "
        + e.getColumnNumber()
        + ").";
  }

  private static void requireValid(final String pom, final Declaration declaration) {
    final Element element =
        find(pom, declaration)
            .orElseThrow(
                () ->
                    failure(
                        declaration,
                        "declaration is not in the project-level <"
                            + declaration.section()
                            + ">."));
    final Set<String> allowed = declaration.dependency() ? DEPENDENCY_CHILDREN : PLUGIN_CHILDREN;
    for (final Element child : PomElements.children(pom, element)) {
      if (!allowed.contains(child.name())) {
        throw failure(
            declaration,
            "declaration contains <"
                + child.name()
                + ">, which Maven does not allow directly inside <"
                + element.name()
                + ">.");
      }
    }
  }

  private static Optional<Element> find(final String pom, final Declaration declaration) {
    Optional<Element> section = Optional.of(PomElements.project(pom));
    for (final String name : declaration.path()) {
      section = section.flatMap(parent -> PomElements.child(pom, parent, name));
    }
    return section.flatMap(
        parent -> PomElements.declaration(pom, parent, declaration.artifactId()));
  }

  private static SentinelException failure(final Declaration declaration, final String reason) {
    LOGGER.log(
        Level.WARNING, () -> "event=pom-validation status=failed tool=" + declaration.artifactId());
    return new SentinelException(UNSAFE + "generated " + declaration.artifactId() + " " + reason);
  }

  /** A declaration init added in this run, identified by artifact and kind. */
  record Declaration(String artifactId, boolean dependency) {
    List<String> path() {
      return dependency ? List.of("dependencies") : List.of("build", "plugins");
    }

    String section() {
      return dependency ? "dependencies" : "build/plugins";
    }
  }
}
