package pl.mkn.tdw.localworkspace.tokens;

import java.util.List;
import java.util.Optional;

public interface LocalAccessTokenStore {

    List<LocalAccessTokenRecord> listTokens();

    Optional<LocalAccessTokenRecord> findByRef(String tokenRef);

    void save(LocalAccessTokenRecord token);

    void delete(String tokenRef);
}
