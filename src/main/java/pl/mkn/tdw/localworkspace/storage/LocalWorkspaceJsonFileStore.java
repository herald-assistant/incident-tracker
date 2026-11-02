package pl.mkn.tdw.localworkspace.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.localworkspace.LocalWorkspaceStorageException;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class LocalWorkspaceJsonFileStore {

    private static final int MAX_MOVE_ATTEMPTS = 5;
    private static final long MOVE_RETRY_DELAY_MILLIS = 50;

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
            moveWithRetry(temporaryPath, path);
        } catch (IOException exception) {
            throw new LocalWorkspaceStorageException("Failed to write local workspace JSON file: " + path, exception);
        } finally {
            deleteTemporary(temporaryPath);
        }
    }

    private void moveWithRetry(Path source, Path target) throws IOException {
        for (var attempt = 1; ; attempt++) {
            try {
                moveAtomic(source, target);
                return;
            } catch (AccessDeniedException exception) {
                if (attempt >= MAX_MOVE_ATTEMPTS) {
                    throw exception;
                }
                log.warn("Access denied replacing local workspace file path={} attempt={}/{}; retrying error={}",
                        target, attempt, MAX_MOVE_ATTEMPTS, errorSummary(exception));
                try {
                    Thread.sleep(MOVE_RETRY_DELAY_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    var failure = new InterruptedIOException(
                            "Interrupted while retrying local workspace file replacement: " + target);
                    failure.initCause(interrupted);
                    failure.addSuppressed(exception);
                    throw failure;
                }
            }
        }
    }

    private void moveAtomic(Path source, Path target) throws IOException {
        try {
            move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    protected void move(Path source, Path target, CopyOption... options) throws IOException {
        Files.move(source, target, options);
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
