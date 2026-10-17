package dev.sentinel.infrastructure.init;

import dev.sentinel.domain.config.SentinelException;

/**
 * Scans element tags in pom.xml text, skipping comments, CDATA sections, processing instructions,
 * and declarations.
 */
final class PomTags {
  private PomTags() {}

  /** The next element tag in {@code [from, to)}, or {@code null} when none remains. */
  static Tag next(final String xml, final int from, final int to) {
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

  static int skipPast(final String xml, final int from, final String terminator) {
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

  /** An element tag spanning {@code [start, end)}. */
  record Tag(int start, int end, String name, boolean closing, boolean selfClosing) {}
}
