package pl.mkn.tdw.localworkspace.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mkn.tdw.localworkspace.LocalWorkspaceStorageException;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalWorkspaceJsonFileStoreTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void shouldRetryAccessDeniedAndReplaceExistingFile() throws Exception {
        var target = existingFile();
        var store = new FailingMovesJsonFileStore(
                new AccessDeniedException(target.toString()),
                new AccessDeniedException(target.toString())
        );

        store.writeAtomic(target, Map.of("system", "CRM"));

        assertEquals(Map.of("system", "CRM"), objectMapper.readValue(target.toFile(), Map.class));
        assertEquals(List.of(atomicOptions(), atomicOptions(), atomicOptions()), store.attempts);
        assertNoTemporaryFiles(target);
    }

    @Test
    void shouldFailAfterFiveAccessDeniedAttemptsAndPreserveExistingFile() throws Exception {
        var target = existingFile();
        var denied = new AccessDeniedException(target.toString());
        var store = new FailingMovesJsonFileStore(denied, denied, denied, denied, denied);

        var failure = assertThrows(LocalWorkspaceStorageException.class,
                () -> store.writeAtomic(target, Map.of("system", "CRM")));

        assertSame(denied, failure.getCause());
        assertEquals(5, store.attempts.size());
        assertEquals("original", Files.readString(target));
        assertNoTemporaryFiles(target);
    }

    @Test
    void shouldNotRetryOtherIoFailures() throws Exception {
        var target = existingFile();
        var ioFailure = new IOException("CRM test filesystem failure");
        var store = new FailingMovesJsonFileStore(ioFailure);

        var failure = assertThrows(LocalWorkspaceStorageException.class,
                () -> store.writeAtomic(target, Map.of("system", "CRM")));

        assertSame(ioFailure, failure.getCause());
        assertEquals(1, store.attempts.size());
        assertEquals("original", Files.readString(target));
        assertNoTemporaryFiles(target);
    }

    @Test
    void shouldStopRetryingWhenInterruptedAndRestoreInterruptFlag() throws Exception {
        var target = existingFile();
        var denied = new AccessDeniedException(target.toString());
        var store = new FailingMovesJsonFileStore(denied);

        try {
            Thread.currentThread().interrupt();

            var failure = assertThrows(LocalWorkspaceStorageException.class,
                    () -> store.writeAtomic(target, Map.of("system", "CRM")));

            assertTrue(Thread.currentThread().isInterrupted());
            var cause = assertInstanceOf(InterruptedIOException.class, failure.getCause());
            assertInstanceOf(InterruptedException.class, cause.getCause());
            assertEquals(List.of(denied), List.of(cause.getSuppressed()));
            assertEquals(1, store.attempts.size());
        } finally {
            Thread.interrupted();
        }

        assertEquals("original", Files.readString(target));
        assertNoTemporaryFiles(target);
    }

    @Test
    void shouldFallBackWhenAtomicMoveIsUnavailable() throws Exception {
        var target = existingFile();
        var store = new FailingMovesJsonFileStore(atomicMoveUnavailable(target));

        store.writeAtomic(target, Map.of("system", "CRM"));

        assertEquals(Map.of("system", "CRM"), objectMapper.readValue(target.toFile(), Map.class));
        assertEquals(List.of(atomicOptions(), replacementOptions()), store.attempts);
        assertNoTemporaryFiles(target);
    }

    @Test
    void shouldRetryAccessDeniedDuringFallbackReplacement() throws Exception {
        var target = existingFile();
        var store = new FailingMovesJsonFileStore(
                atomicMoveUnavailable(target),
                new AccessDeniedException(target.toString()),
                atomicMoveUnavailable(target)
        );

        store.writeAtomic(target, Map.of("system", "CRM"));

        assertEquals(Map.of("system", "CRM"), objectMapper.readValue(target.toFile(), Map.class));
        assertEquals(List.of(
                atomicOptions(), replacementOptions(), atomicOptions(), replacementOptions()
        ), store.attempts);
        assertNoTemporaryFiles(target);
    }

    private Path existingFile() throws IOException {
        var target = tempDir.resolve("index.json");
        Files.writeString(target, "original");
        return target;
    }

    private AtomicMoveNotSupportedException atomicMoveUnavailable(Path target) {
        return new AtomicMoveNotSupportedException(
                "CRM temporary file", target.toString(), "CRM test filesystem does not support atomic moves");
    }

    private List<CopyOption> atomicOptions() {
        return List.of(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private List<CopyOption> replacementOptions() {
        return List.of(StandardCopyOption.REPLACE_EXISTING);
    }

    private void assertNoTemporaryFiles(Path target) throws IOException {
        try (var files = Files.list(tempDir)) {
            assertEquals(List.of(target), files.toList());
        }
    }

    private final class FailingMovesJsonFileStore extends LocalWorkspaceJsonFileStore {

        private final List<IOException> failures;
        private final List<List<CopyOption>> attempts = new ArrayList<>();

        private FailingMovesJsonFileStore(IOException... failures) {
            super(objectMapper);
            this.failures = List.of(failures);
        }

        @Override
        protected void move(Path source, Path target, CopyOption... options) throws IOException {
            attempts.add(List.of(options));
            if (attempts.size() <= failures.size()) {
                throw failures.get(attempts.size() - 1);
            }
            super.move(source, target, options);
        }
    }
}
