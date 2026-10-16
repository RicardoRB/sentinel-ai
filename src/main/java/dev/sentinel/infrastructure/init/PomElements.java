package dev.sentinel.infrastructure.init;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.infrastructure.init.PomTags.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Locates direct child elements of a pom.xml by nesting depth, so profiles, plugin management, and
 * plugin dependencies are never mistaken for the project's own elements.
 */
final class PomElements {
  private PomElements() {}

  /** The non-empty {@code <project>} root element. */
  static Element project(final String pom) {
    return child(pom, new Element("", 0, 0, pom.length(), false), "project")
        .filter(element -> !element.selfClosing())
        .orElseThrow(
            () ->
                new SentinelException("Cannot safely update pom.xml: missing <project> element."));
  }

  /** Finds the first direct child of {@code parent} named {@code name}. */
  static Optional<Element> child(final String xml, final Element parent, final String name) {
    final Tag open = opening(xml, parent, name);
    return open == null ? Optional.empty() : Optional.of(element(xml, open, parent));
  }

  /** Lists the direct child elements of {@code parent} in document order. */
  static List<Element> children(final String xml, final Element parent) {
    final List<Element> children = new ArrayList<>();
    Tag tag = PomTags.next(xml, parent.openEnd(), parent.closeStart());
    while (tag != null) {
      final Element child = element(xml, tag, parent);
      children.add(child);
      tag = PomTags.next(xml, end(xml, child), parent.closeStart());
    }
    return children;
  }

  /** The trimmed character content of {@code element}, which is empty when it is self-closing. */
  static String text(final String xml, final Element element) {
    return xml.substring(element.openEnd(), element.closeStart()).strip();
  }

  /** Finds the direct child of {@code section} whose {@code <artifactId>} is {@code artifact}. */
  static Optional<Element> declaration(
      final String xml, final Element section, final String artifact) {
    return children(xml, section).stream()
        .filter(
            child ->
                child(xml, child, "artifactId")
                    .map(id -> text(xml, id).equals(artifact))
                    .orElse(false))
        .findFirst();
  }

  private static Element element(final String xml, final Tag open, final Element parent) {
    if (open.closing()) {
      throw new SentinelException("Cannot safely update pom.xml: malformed XML.");
    }
    if (open.selfClosing()) {
      return new Element(open.name(), open.start(), open.end(), open.end(), true);
    }
    final int closeStart = matchingClose(xml, open, parent.closeStart());
    return new Element(open.name(), open.start(), open.end(), closeStart, false);
  }

  /** The offset just past the element's closing tag, or past the tag itself when self-closing. */
  private static int end(final String xml, final Element element) {
    return element.selfClosing()
        ? element.openEnd()
        : PomTags.skipPast(xml, element.closeStart(), ">");
  }

  private static Tag opening(final String xml, final Element parent, final String name) {
    int depth = 0;
    Tag tag = PomTags.next(xml, parent.openEnd(), parent.closeStart());
    while (tag != null && (depth != 0 || tag.closing() || !name.equals(tag.name()))) {
      depth += depthChange(tag);
      tag = PomTags.next(xml, tag.end(), parent.closeStart());
    }
    return tag;
  }

  private static int matchingClose(final String xml, final Tag open, final int to) {
    int depth = 1;
    Tag tag = PomTags.next(xml, open.end(), to);
    while (tag != null) {
      depth += depthChange(tag);
      if (depth == 0 && open.name().equals(tag.name())) {
        return tag.start();
      }
      if (depth == 0) {
        break;
      }
      tag = PomTags.next(xml, tag.end(), to);
    }
    throw new SentinelException("Cannot safely update pom.xml: malformed XML.");
  }

  private static int depthChange(final Tag tag) {
    if (tag.closing()) {
      return -1;
    }
    return tag.selfClosing() ? 0 : 1;
  }

  /**
   * A named element and its character offsets: its opening tag spans {@code [openStart, openEnd)}
   * and its closing tag starts at {@code closeStart}. A self-closing element has no content.
   */
  record Element(String name, int openStart, int openEnd, int closeStart, boolean selfClosing) {}
}
