package dev.ward.infrastructure.learning;

import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import dev.ward.domain.learning.LearningLedger;
import dev.ward.domain.learning.LearningRecord;
import dev.ward.domain.learning.LearningState;
import dev.ward.domain.learning.LearningStore;
import dev.ward.domain.learning.LearningStoreException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.fory.json.annotation.JsonType;

/** Stores the learning ledger in {@code .ward/learning.json}. */
public final class JsonLearningStore implements LearningStore {
  private static final String DIRECTORY = ".ward";
  private static final String FILE = "learning.json";
  private static final int LEGACY_FORMAT_VERSION = 1;
  private final JsonCodec codec;

  /** Creates the store; {@code codec} reads and writes the JSON file. */
  @Inject
  public JsonLearningStore(final JsonCodec codec) {
    this.codec = codec;
  }

  @Override
  public Optional<LearningLedger> load(final Path projectRoot) {
    final Path path = path(projectRoot);
    if (!Files.exists(path)) {
      return Optional.empty();
    }
    try {
      final FileDto file = codec.fromJson(Files.readString(path), FileDto.class);
      return Optional.of(toDomain(file));
    } catch (IOException | JsonCodecException exception) {
      throw new LearningStoreException("Could not read learning store " + path, exception);
    }
  }

  @Override
  public void save(final Path projectRoot, final LearningLedger ledger) {
    final Path directory = projectRoot.resolve(DIRECTORY);
    final Path target = directory.resolve(FILE);
    final Path temporary = directory.resolve(FILE + "." + System.nanoTime() + ".tmp");
    try {
      Files.createDirectories(directory);
      Files.writeString(temporary, codec.toJson(fromDomain(ledger)));
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException exception) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException exception) {
      throw new LearningStoreException("Could not write learning store " + target, exception);
    } finally {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {
        // A failed cleanup must not hide the original persistence error.
      }
    }
  }

  private static Path path(final Path root) {
    return root.resolve(DIRECTORY).resolve(FILE);
  }

  private static LearningLedger toDomain(final FileDto file) {
    if (file != null && file.formatVersion == LEGACY_FORMAT_VERSION) {
      // Version 1 tracked gate statuses, which cannot be mapped to rules, so start over.
      return LearningLedger.empty();
    }
    if (file == null || file.formatVersion != LearningLedger.CURRENT_FORMAT_VERSION) {
      throw new LearningStoreException("Unsupported learning format version");
    }
    final Map<String, LearningRecord> records = new LinkedHashMap<>();
    if (file.records != null) {
      file.records.forEach(
          (key, value) -> {
            final LearningRecord record = toRecord(value);
            if (!key.equals(record.key())) {
              throw new LearningStoreException("Learning record key does not match its value");
            }
            records.put(key, record);
          });
    }
    try {
      return new LearningLedger(file.formatVersion, records);
    } catch (IllegalArgumentException exception) {
      throw new LearningStoreException("Invalid learning ledger", exception);
    }
  }

  private static LearningRecord toRecord(final RecordDto value) {
    if (value == null) {
      throw new LearningStoreException("Learning record must not be null");
    }
    if (Stream.of(
            value.key,
            value.gate,
            value.rule,
            value.summary,
            value.state,
            value.firstSeen,
            value.lastSeen)
        .anyMatch(Objects::isNull)) {
      throw new LearningStoreException("Invalid learning record: missing field");
    }
    try {
      return new LearningRecord(
          value.key,
          value.gate,
          value.rule,
          value.summary,
          value.occurrences,
          LearningState.valueOf(value.state),
          Instant.parse(value.firstSeen),
          Instant.parse(value.lastSeen),
          value.prompted);
    } catch (IllegalArgumentException | DateTimeParseException exception) {
      throw new LearningStoreException("Invalid learning record", exception);
    }
  }

  private static FileDto fromDomain(final LearningLedger ledger) {
    final FileDto file = new FileDto();
    file.formatVersion = ledger.formatVersion();
    final Map<String, RecordDto> records = new LinkedHashMap<>();
    ledger.records().forEach((key, value) -> records.put(key, RecordDto.from(value)));
    file.records = records;
    return file;
  }

  /** The file schema; its public fields are serialized. */
  @JsonType
  public static final class FileDto {
    public int formatVersion;
    public Map<String, RecordDto> records;
  }

  /** One record in the file schema; its public fields are serialized. */
  @JsonType
  public static final class RecordDto {
    public String key;
    public String gate;
    public String rule;
    public String summary;
    public int occurrences;
    public String state;
    public String firstSeen;
    public String lastSeen;
    public boolean prompted;

    static RecordDto from(final LearningRecord value) {
      final RecordDto dto = new RecordDto();
      dto.key = value.key();
      dto.gate = value.gate();
      dto.rule = value.rule();
      dto.summary = value.summary();
      dto.occurrences = value.occurrences();
      dto.state = value.state().name();
      dto.firstSeen = value.firstSeen().toString();
      dto.lastSeen = value.lastSeen().toString();
      dto.prompted = value.prompted();
      return dto;
    }
  }
}
