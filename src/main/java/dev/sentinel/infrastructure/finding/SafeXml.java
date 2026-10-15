package dev.sentinel.infrastructure.finding;

import dev.sentinel.domain.gate.FindingExtractionException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** StAX reading with DTDs and external entities disabled, so reports cannot trigger XXE. */
final class SafeXml {
  private SafeXml() {}

  /** Receives each start element; the reader is positioned on that element. */
  @FunctionalInterface
  interface ElementVisitor {
    void startElement(XMLStreamReader reader) throws XMLStreamException;
  }

  static void read(final Path file, final ElementVisitor visitor) {
    final XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
    try (InputStream input = Files.newInputStream(file)) {
      final XMLStreamReader reader = factory.createXMLStreamReader(input);
      try {
        while (reader.hasNext()) {
          if (reader.next() == XMLStreamReader.START_ELEMENT) {
            visitor.startElement(reader);
          }
        }
      } finally {
        reader.close();
      }
    } catch (XMLStreamException exception) {
      throw new MalformedReportException(file, exception);
    } catch (IOException exception) {
      throw new FindingExtractionException("Cannot read report", exception);
    }
  }

  static Integer intAttribute(final XMLStreamReader reader, final String name) {
    final String value = reader.getAttributeValue(null, name);
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      final int parsed = Integer.parseInt(value.trim());
      return parsed > 0 ? parsed : null;
    } catch (NumberFormatException exception) {
      return null;
    }
  }
}
