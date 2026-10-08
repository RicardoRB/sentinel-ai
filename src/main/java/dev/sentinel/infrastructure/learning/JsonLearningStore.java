package dev.sentinel.infrastructure.learning;

import dev.sentinel.domain.json.JsonCodec;
import dev.sentinel.domain.learning.LearningLedger;
import dev.sentinel.domain.learning.LearningRecord;
import dev.sentinel.domain.learning.LearningState;
import dev.sentinel.domain.learning.LearningStore;
import dev.sentinel.domain.learning.LearningStoreException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import org.apache.fory.json.annotation.JsonType;

public final class JsonLearningStore implements LearningStore {
  private static final String DIRECTORY = ".sentinel";
  private static final String FILE = "learning.json";
  private final JsonCodec codec;

  @Inject
  public JsonLearningStore(JsonCodec codec) {
    this.codec = codec;
  }

  @Override
  public Optional<LearningLedger> load(Path projectRoot) throws LearningStoreException {
    Path path = path(projectRoot);
    if (!Files.exists(path)) {
      return Optional.empty();
    }
    try {
      FileDto file = codec.fromJson(Files.readString(path), FileDto.class);
      return Optional.of(toDomain(file));
    } catch (LearningStoreException storeException) {
      throw storeException;
    } catch (Exception exception) {
      throw new LearningStoreException("Could not read learning store " + path, exception);
    }
  }

  @Override
  public void save(Path projectRoot, LearningLedger ledger) throws LearningStoreException {
    Path directory = projectRoot.resolve(DIRECTORY);
    Path target = directory.resolve(FILE);
    Path temporary = directory.resolve(FILE + "." + System.nanoTime() + ".tmp");
    try {
      Files.createDirectories(directory);
      Files.writeString(temporary, codec.toJson(fromDomain(ledger)));
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException exception) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException | RuntimeException exception) {
      throw new LearningStoreException("Could not write learning store " + target, exception);
    } finally {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {
        // A failed cleanup must not hide the original persistence error.
      }
    }
  }

  private static Path path(Path root) {
    return root.resolve(DIRECTORY).resolve(FILE);
  }

  private static LearningLedger toDomain(FileDto file) {
    if (file == null || file.formatVersion != LearningLedger.CURRENT_FORMAT_VERSION) {
      throw new LearningStoreException("Unsupported learning format version");
    }
    Map<String, LearningRecord> records = new LinkedHashMap<>();
    if (file.records != null) {
      file.records.forEach(
          (key, value) -> {
            if (value == null) {
              throw new LearningStoreException("Learning record must not be null");
            }
            try {
              LearningRecord record =
                  new LearningRecord(
                      value.key,
                      value.gate,
                      value.status,
                      value.summary,
                      value.occurrences,
                      LearningState.valueOf(value.state),
                      Instant.parse(value.firstSeen),
                      Instant.parse(value.lastSeen),
                      value.prompted);
              if (!key.equals(record.key())) {
                throw new LearningStoreException("Learning record key does not match its value");
              }
              records.put(key, record);
            } catch (LearningStoreException exception) {
              throw exception;
            } catch (RuntimeException exception) {
              throw new LearningStoreException("Invalid learning record", exception);
            }
          });
    }
    try {
      return new LearningLedger(file.formatVersion, records);
    } catch (RuntimeException exception) {
      throw new LearningStoreException("Invalid learning ledger", exception);
    }
  }

  private static FileDto fromDomain(LearningLedger ledger) {
    FileDto file = new FileDto();
    file.formatVersion = ledger.formatVersion();
    file.records = new LinkedHashMap<>();
    ledger.records().forEach((key, value) -> file.records.put(key, RecordDto.from(value)));
    return file;
  }

  @JsonType
  public static final class FileDto {
    public int formatVersion;
    public Map<String, RecordDto> records;
  }

  @JsonType
  public static final class RecordDto {
    public String key;
    public String gate;
    public String status;
    public String summary;
    public int occurrences;
    public String state;
    public String firstSeen;
    public String lastSeen;
    public boolean prompted;

    static RecordDto from(LearningRecord value) {
      RecordDto dto = new RecordDto();
      dto.key = value.key();
      dto.gate = value.gate();
      dto.status = value.status();
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
