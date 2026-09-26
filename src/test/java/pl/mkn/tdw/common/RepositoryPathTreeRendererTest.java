package pl.mkn.tdw.common;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoryPathTreeRendererTest {

    @Test
    void rendersNestedDirectoriesAndFilesInStableDirectoryFirstOrder() {
        var entries = List.of(
                new RepositoryPathTreeRenderer.Entry("README.md", false),
                new RepositoryPathTreeRenderer.Entry("src/components/Navbar", true),
                new RepositoryPathTreeRenderer.Entry("src/components/Button/Button.tsx", false),
                new RepositoryPathTreeRenderer.Entry("public/index.html", false),
                new RepositoryPathTreeRenderer.Entry("src", true),
                new RepositoryPathTreeRenderer.Entry("public", true),
                new RepositoryPathTreeRenderer.Entry("src/components", true),
                new RepositoryPathTreeRenderer.Entry("src/components/Button", true)
        );

        assertThat(RepositoryPathTreeRenderer.render("CRM/crm-ui", entries)).isEqualTo("""
                crm-ui/
                ├── public/
                │   └── index.html
                ├── src/
                │   └── components/
                │       ├── Button/
                │       │   └── Button.tsx
                │       └── Navbar/
                └── README.md""");
        var reversed = new ArrayList<>(entries);
        Collections.reverse(reversed);
        assertThat(RepositoryPathTreeRenderer.render("CRM/crm-ui", reversed))
                .isEqualTo(RepositoryPathTreeRenderer.render("CRM/crm-ui", entries));
    }

    @Test
    void rendersEmptyAndPartialSnapshotsWithoutInventingPlaceholders() {
        assertThat(RepositoryPathTreeRenderer.render("CRM/crm-ui", List.of())).isEqualTo("crm-ui/");
        assertThat(RepositoryPathTreeRenderer.render("CRM/crm-ui", List.of(
                new RepositoryPathTreeRenderer.Entry("src", true))))
                .isEqualTo("crm-ui/\n└── src/");
    }

    @Test
    void rejectsConflictingOrMalformedPaths() {
        assertThatThrownBy(() -> RepositoryPathTreeRenderer.render("CRM/crm-ui", List.of(
                new RepositoryPathTreeRenderer.Entry("src", false),
                new RepositoryPathTreeRenderer.Entry("src/Button.tsx", false))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RepositoryPathTreeRenderer.render("CRM/crm-ui", List.of(
                new RepositoryPathTreeRenderer.Entry("../outside", true))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RepositoryPathTreeRenderer.render("CRM/crm-ui", List.of(
                new RepositoryPathTreeRenderer.Entry("src/Button.tsx", false))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parent was not observed");
    }
}
