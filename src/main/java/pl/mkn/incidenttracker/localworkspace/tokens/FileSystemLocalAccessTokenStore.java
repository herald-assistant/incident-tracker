package pl.mkn.incidenttracker.localworkspace.tokens;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.localworkspace.LocalWorkspaceProperties;
import pl.mkn.incidenttracker.localworkspace.storage.LocalWorkspaceJsonFileStore;
import pl.mkn.incidenttracker.localworkspace.storage.LocalWorkspacePaths;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FileSystemLocalAccessTokenStore implements LocalAccessTokenStore {

    private final LocalWorkspaceProperties properties;
    private final LocalWorkspacePaths paths;
    private final LocalWorkspaceJsonFileStore jsonFileStore;

    @Override
    public List<LocalAccessTokenRecord> listTokens() {
        if (!properties.isEnabled()) {
            return List.of();
        }

        return readTokenFile().tokens().stream()
                .sorted(Comparator.comparing(LocalAccessTokenRecord::updatedAt).reversed())
                .toList();
    }

    @Override
    public Optional<LocalAccessTokenRecord> findByRef(String tokenRef) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }

        return readTokenFile().tokens().stream()
                .filter(token -> token.tokenRef().equals(tokenRef))
                .findFirst();
    }

    @Override
    public void save(LocalAccessTokenRecord token) {
        if (!properties.isEnabled()) {
            return;
        }

        var tokenFile = readTokenFile();
        var tokens = tokenFile.tokens().stream()
                .filter(existing -> !existing.tokenRef().equals(token.tokenRef()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        tokens.add(token);
        jsonFileStore.writeAtomic(
                paths.tokensFile(),
                new LocalAccessTokenFile(tokenFile.schema(), tokenFile.version(), tokens)
        );
    }

    @Override
    public void delete(String tokenRef) {
        if (!properties.isEnabled()) {
            return;
        }

        var tokenFile = readTokenFile();
        var tokens = tokenFile.tokens().stream()
                .filter(token -> !token.tokenRef().equals(tokenRef))
                .toList();
        jsonFileStore.writeAtomic(
                paths.tokensFile(),
                new LocalAccessTokenFile(tokenFile.schema(), tokenFile.version(), tokens)
        );
    }

    private LocalAccessTokenFile readTokenFile() {
        return jsonFileStore.read(paths.tokensFile(), LocalAccessTokenFile.class)
                .orElseGet(LocalAccessTokenFile::empty);
    }
}
