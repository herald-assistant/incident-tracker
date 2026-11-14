package pl.mkn.tdw;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import pl.mkn.tdw.features.configdriftviewer.source.ConfigDriftViewerRepositoryProperties;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceApplicationPropertiesTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldKeepPackagedDefaultsWhenWorkspaceFileIsMissing() {
        try (var context = start(temporaryDirectory)) {
            assertEquals("TeamDeliveryWorkspace", context.getEnvironment().getProperty("spring.application.name"));
            assertEquals(20, context.getBean(ConfigDriftViewerRepositoryProperties.class).getMaxParallelComponents());
        }
    }

    @Test
    void shouldReadDefaultWorkspaceRelativeToProcessWorkingDirectory() throws Exception {
        var workspace = Files.createDirectory(temporaryDirectory.resolve("tdw-data"));
        Files.writeString(workspace.resolve("application.properties"),
                "spring.application.name=CRM default workspace\n");
        var javaDirectory = Path.of(System.getProperty("java.home"), "bin");
        var javaExecutable = javaDirectory.resolve(Files.exists(javaDirectory.resolve("java.exe")) ? "java.exe" : "java");
        var output = temporaryDirectory.resolve("bootstrap.log");
        var processBuilder = new ProcessBuilder(javaExecutable.toString(), "-cp",
                System.getProperty("java.class.path"), DefaultWorkspaceProbe.class.getName())
                .directory(temporaryDirectory.toFile()).redirectErrorStream(true).redirectOutput(output.toFile());
        processBuilder.environment().remove("TDW_WORKSPACE_DIRECTORY");
        var process = processBuilder.start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Configuration bootstrap timed out");
            assertEquals(0, process.exitValue(), () -> readOutput(output));
            assertTrue(readOutput(output).contains("workspace-name=CRM default workspace"));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    @Test
    void shouldOverrideAndBindPropertiesFromWorkspacePathWithSpaces() throws Exception {
        var workspace = Files.createDirectory(temporaryDirectory.resolve("CRM workspace"));
        Files.writeString(workspace.resolve("application.properties"), """
                spring.application.name=CRM test workspace
                features.config-drift-viewer.branches=dev,uat
                features.config-drift-viewer.repositories.crm-config.display-name=CRM configuration
                features.config-drift-viewer.repositories.crm-config.connection-id=runtime-config
                features.config-drift-viewer.repositories.crm-config.project-path=CRM/runtime-configuration
                """);

        try (var context = start(workspace)) {
            assertEquals("CRM test workspace", context.getEnvironment().getProperty("spring.application.name"));
            var properties = context.getBean(ConfigDriftViewerRepositoryProperties.class);
            assertEquals(List.of("dev", "uat"), properties.getBranches());
            assertEquals("CRM/runtime-configuration", properties.getRepositories().get("crm-config").getProjectPath());
            assertEquals(20, properties.getMaxParallelComponents());
            assertEquals(workspace.toString(), context.getBean(LocalWorkspaceProperties.class).getDirectory());
        }
    }

    @Test
    void shouldKeepCommandLinePropertiesAboveWorkspaceOverrides() throws Exception {
        Files.writeString(temporaryDirectory.resolve("application.properties"),
                "spring.application.name=CRM workspace file\n");

        try (var context = start(temporaryDirectory, "--spring.application.name=CRM command line")) {
            assertEquals("CRM command line", context.getEnvironment().getProperty("spring.application.name"));
        }
    }

    @Test
    void shouldResolveWorkspaceFromEnvironmentVariableBeforeImport() throws Exception {
        Files.writeString(temporaryDirectory.resolve("application.properties"),
                "spring.application.name=CRM environment workspace\n");
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                "crm-test-environment", Map.of("TDW_WORKSPACE_DIRECTORY", temporaryDirectory.toString())));
        var application = application();
        application.setEnvironment(environment);

        try (var context = application.run("--spring.config.location=classpath:/application.properties")) {
            assertEquals("CRM environment workspace", context.getEnvironment().getProperty("spring.application.name"));
        }
    }

    @Test
    void shouldFailForInvalidExistingWorkspaceProperties() throws Exception {
        Files.writeString(temporaryDirectory.resolve("application.properties"), "crm.invalid=\\uZZZZ\n");

        assertThrows(IllegalStateException.class, () -> {
            try (var ignored = start(temporaryDirectory)) {
                // An existing unreadable document must not silently fall back to defaults.
            }
        });
    }

    private ConfigurableApplicationContext start(Path workspace, String... additionalArguments) {
        var arguments = new ArrayList<>(List.of(
                "--spring.config.location=classpath:/application.properties",
                "--tdw.workspace.directory=" + workspace));
        arguments.addAll(List.of(additionalArguments));
        return application().run(arguments.toArray(String[]::new));
    }

    private static SpringApplication application() {
        var application = new SpringApplication(BootstrapConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        application.setDefaultProperties(Map.of("spring.main.banner-mode", "off"));
        return application;
    }

    private static String readOutput(Path output) {
        try {
            return Files.readString(output);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    public static class DefaultWorkspaceProbe {
        public static void main(String[] arguments) {
            try (var context = application().run("--spring.config.location=classpath:/application.properties")) {
                System.out.println("workspace-name=" + context.getEnvironment().getProperty("spring.application.name"));
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties({LocalWorkspaceProperties.class, ConfigDriftViewerRepositoryProperties.class})
    static class BootstrapConfiguration {
    }
}
