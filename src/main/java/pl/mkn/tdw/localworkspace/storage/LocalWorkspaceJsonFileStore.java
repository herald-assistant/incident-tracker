package pl.mkn.tdw.localworkspace.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.localworkspace.LocalWorkspaceStorageException;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class LocalWorkspaceJsonFileStore {

    private final ObjectMapper objectMapper;

    public <T> Optional<T> read(Path path, Class<T> type) {
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(path.toFile(), type));
        } catch (IOException exception) {
            log.warn("Failed to read local workspace JSON file path={} error={}", path, errorSummary(exception));
            return Optional.empty();
        }
    }

    public void writeAtomic(Path path, Object value) {
        Path temporaryPath = null;
        try {
            Files.createDirectories(path.getParent());
            temporaryPath = path.resolveSibling(path.getFileName() + ".tmp-" + UUID.randomUUID());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporaryPath.toFile(), value);
            moveAtomic(temporaryPath, path);
        } catch (IOException exception) {
            throw new LocalWorkspaceStorageException("Failed to write local workspace JSON file: " + path, exception);
        } finally {
            deleteTemporary(temporaryPath);
        }
    }

    private void moveAtomic(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteTemporary(Path temporaryPath) {
        if (temporaryPath == null) {
            return;
        }

        try {
            Files.deleteIfExists(temporaryPath);
        } catch (IOException exception) {
            log.warn("Failed to delete temporary local workspace file path={} error={}", temporaryPath, errorSummary(exception));
        }
    }

    private String errorSummary(IOException exception) {
        if (exception instanceof JsonProcessingException jsonProcessingException) {
            return exception.getClass().getSimpleName() + ": " + jsonProcessingException.getOriginalMessage();
        }
        return exception.getClass().getSimpleName() + ": " + exception.getMessage();
    }
}
