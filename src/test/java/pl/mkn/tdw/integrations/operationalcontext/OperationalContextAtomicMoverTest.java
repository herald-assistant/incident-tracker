package pl.mkn.tdw.integrations.operationalcontext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalContextAtomicMoverTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldFallBackForCrmSeedDirectoryWhenAtomicMoveIsUnavailable() throws Exception {
        var source = Files.createDirectory(temporaryDirectory.resolve("crm-staging-seed"));
        Files.writeString(source.resolve("crm-document.yml"), "systems: []\n");
        var target = temporaryDirectory.resolve("crm-installed-copy");
        var mover = new AtomicMoveUnavailableOnceMover();

        mover.moveDirectory(source, target);

        assertFalse(Files.exists(source));
        assertTrue(Files.isRegularFile(target.resolve("crm-document.yml")));
        assertEquals(2, mover.attempts());
    }

    @Test
    void shouldFallBackForCrmDocumentReplacementWhenAtomicMoveIsUnavailable() throws Exception {
        var source = temporaryDirectory.resolve("crm-document-temp.yml");
        var target = temporaryDirectory.resolve("crm-document.yml");
        Files.writeString(source, "new-crm-document");
        Files.writeString(target, "old-crm-document");
        var mover = new AtomicMoveUnavailableOnceMover();

        mover.replaceFile(source, target);

        assertFalse(Files.exists(source));
        assertEquals("new-crm-document", Files.readString(target));
        assertEquals(2, mover.attempts());
    }

    private static final class AtomicMoveUnavailableOnceMover extends OperationalContextAtomicMover {

        private int attempts;

        @Override
        protected void move(Path source, Path target, CopyOption... options) throws IOException {
            attempts++;
            for (var option : options) {
                if (option == StandardCopyOption.ATOMIC_MOVE) {
                    throw new AtomicMoveNotSupportedException(
                            source.toString(),
                            target.toString(),
                            "anonymous CRM test filesystem"
                    );
                }
            }
            super.move(source, target, options);
        }

        int attempts() {
            return attempts;
        }
    }
}
