package dev.sentinel.infrastructure.init;

import dev.sentinel.domain.config.SentinelException;
import java.util.Optional;

/**
 * Locates direct child elements of a pom.xml by nesting depth, so profiles, plugin management, and
 * plugin dependencies are never mistaken for the project's own elements. Comments, CDATA sections,
 * and processing instructions are skipped.
 */
final class PomElements {
  private PomElements() {}

  /** The non-empty {@code <project>} root element. */
  static Element project(final String pom) {
    return child(pom, new Element(0, 0, pom.length(), false), "project")
        .filter(element -> !element.selfClosing())
        .orElseThrow(
            () ->
                new SentinelException("Cannot safely update pom.xml: missing <project> element."));
  }

  /** Finds the first direct child of {@code parent} named {@code name}. */
  static Optional<Element> child(final String xml, final Element parent, final String name) {
    final Tag open = opening(xml, parent, name);
    if (open == null) {
      return Optional.empty();
    }
    if (open.selfClosing()) {
      return Optional.of(new Element(open.start(), open.end(), open.end(), true));
    }
    final int closeStart = matchingClose(xml, open.end(), parent.closeStart());
    return Optional.of(new Element(open.start(), open.end(), closeStart, false));
  }

  private static Tag opening(final String xml, final Element parent, final String name) {
    int depth = 0;
    Tag tag = nextTag(xml, parent.openEnd(), parent.closeStart());
    while (tag != null && (depth != 0 || tag.closing() || !name.equals(tag.name()))) {
      depth += depthChange(tag);
      tag = nextTag(xml, tag.end(), parent.closeStart());
    }
    return tag;
  }

  private static int matchingClose(final String xml, final int from, final int to) {
    int depth = 1;
    Tag tag = nextTag(xml, from, to);
    while (tag != null) {
      depth += depthChange(tag);
      if (depth == 0) {
        return tag.start();
      }
      tag = nextTag(xml, tag.end(), to);
    }
    throw new SentinelException("Cannot safely update pom.xml: malformed XML.");
  }

  private static int depthChange(final Tag tag) {
    if (tag.closing()) {
      return -1;
    }
    return tag.selfClosing() ? 0 : 1;
  }

  /** The next element tag in {@code [from, to)}, or {@code null} when none remains. */
  private static Tag nextTag(final String xml, final int from, final int to) {
    int index = from;
    int lt = xml.indexOf('<', index);
    while (lt >= 0 && lt < to && isMarkup(xml, lt)) {
      index = skipMarkup(xml, lt);
      lt = xml.indexOf('<', index);
    }
    if (lt < 0 || lt >= to) {
      return null;
    }
    final int end = skipPast(xml, lt, ">");
    final boolean closing = xml.startsWith("</", lt);
    final int nameStart = closing ? lt + 2 : lt + 1;
    return new Tag(
        lt, end, tagName(xml, nameStart), closing, xml.substring(lt, end).endsWith("/>"));
  }

  private static boolean isMarkup(final String xml, final int lt) {
    return xml.startsWith("<!", lt) || xml.startsWith("<?", lt);
  }

  private static int skipMarkup(final String xml, final int lt) {
    if (xml.startsWith("<!--", lt)) {
      return skipPast(xml, lt, "-->");
    }
    if (xml.startsWith("<![CDATA[", lt)) {
      return skipPast(xml, lt, "]]>");
    }
    return skipPast(xml, lt, ">");
  }

  private static int skipPast(final String xml, final int from, final String terminator) {
    final int found = xml.indexOf(terminator, from);
    if (found < 0) {
      throw new SentinelException("Cannot safely update pom.xml: malformed XML.");
    }
    return found + terminator.length();
  }

  private static String tagName(final String xml, final int start) {
    int end = start;
    while (end < xml.length()
        && !Character.isWhitespace(xml.charAt(end))
        && xml.charAt(end) != '>'
        && xml.charAt(end) != '/') {
      end++;
    }
    return xml.substring(start, end);
  }

  /**
   * Character offsets of an element: its opening tag spans {@code [openStart, openEnd)} and its
   * closing tag starts at {@code closeStart}. A self-closing element has no content.
   */
  record Element(int openStart, int openEnd, int closeStart, boolean selfClosing) {}

  private record Tag(int start, int end, String name, boolean closing, boolean selfClosing) {}
}
