package pl.mkn.tdw.integrations.operationalcontext;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
class OperationalContextAtomicMover {

    void moveDirectory(Path source, Path target) throws IOException {
        try {
            move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            move(source, target);
        }
    }

    void replaceFile(Path source, Path target) throws IOException {
        try {
            move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    protected void move(Path source, Path target, CopyOption... options) throws IOException {
        Files.move(source, target, options);
    }
}
