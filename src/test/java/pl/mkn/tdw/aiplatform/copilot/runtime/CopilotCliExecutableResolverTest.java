package pl.mkn.tdw.aiplatform.copilot.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CopilotCliExecutableResolverTest {

    private final CopilotCliExecutableResolver resolver = new CopilotCliExecutableResolver();

    @Test
    void shouldResolveWindowsCommandToAbsoluteExecutable(@TempDir Path directory) throws IOException {
        var executable = Files.createFile(directory.resolve("copilot.exe"));

        var resolved = resolver.resolve("copilot", null, "Windows 11", directory.toString());

        assertEquals(executable.toAbsolutePath().normalize().toString(), resolved);
    }

    @Test
    void shouldPreferWorkingDirectoryBeforePath(@TempDir Path directory) throws IOException {
        var workingDirectory = Files.createDirectory(directory.resolve("workspace"));
        var pathDirectory = Files.createDirectory(directory.resolve("path"));
        var expected = Files.createFile(workingDirectory.resolve("copilot.exe"));
        Files.createFile(pathDirectory.resolve("copilot.exe"));

        var resolved = resolver.resolve(
                "copilot",
                workingDirectory.toString(),
                "Windows 11",
                pathDirectory.toString()
        );

        assertEquals(expected.toAbsolutePath().normalize().toString(), resolved);
    }

    @Test
    void shouldRejectWindowsShellWrapper(@TempDir Path directory) throws IOException {
        var wrapper = Files.createFile(directory.resolve("copilot.cmd"));

        assertThrows(
                IllegalStateException.class,
                () -> resolver.resolve(wrapper.toString(), null, "Windows 11", null)
        );
    }

    @Test
    void shouldRejectMissingWindowsExecutable(@TempDir Path directory) {
        assertThrows(
                IllegalStateException.class,
                () -> resolver.resolve("copilot", null, "Windows 11", directory.toString())
        );
    }

    @Test
    void shouldRejectMissingAbsoluteWindowsExecutable(@TempDir Path directory) {
        assertThrows(
                IllegalStateException.class,
                () -> resolver.resolve(directory.resolve("copilot.exe").toString(), null, "Windows 11", null)
        );
    }

    @Test
    void shouldResolveWindowsCommandFromWinGetPackage(@TempDir Path localAppData) throws IOException {
        var packageDirectory = Files.createDirectories(
                localAppData.resolve("Microsoft/WinGet/Packages/GitHub.Copilot_TestSource")
        );
        var executable = Files.createFile(packageDirectory.resolve("copilot.exe"));

        var resolved = resolver.resolve("copilot", null, "Windows 11", null, localAppData.toString());

        assertEquals(executable.toAbsolutePath().normalize().toString(), resolved);
    }

    @Test
    void shouldAcceptQuotedAbsoluteWindowsExecutable(@TempDir Path directory) throws IOException {
        var executable = Files.createFile(directory.resolve("copilot.exe"));

        var resolved = resolver.resolve("\"" + executable + "\"", null, "Windows 11", null);

        assertEquals(executable.toAbsolutePath().normalize().toString(), resolved);
    }

    @Test
    void shouldLeaveNonWindowsCommandUnchanged() {
        assertEquals("copilot", resolver.resolve("copilot", null, "Linux", null));
    }
}
