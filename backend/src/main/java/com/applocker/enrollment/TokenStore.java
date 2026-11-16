package com.applocker.enrollment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TokenStore {

    void save(StoredToken token);

    Optional<StoredToken> findById(UUID id);

    Optional<StoredToken> findByLabel(String label);

    List<StoredToken> findAll();

    void delete(UUID id);
}
