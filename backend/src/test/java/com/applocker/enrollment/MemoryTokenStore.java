package com.applocker.enrollment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class MemoryTokenStore implements TokenStore {

    private final ConcurrentHashMap<UUID, StoredToken> tokens = new ConcurrentHashMap<>();

    @Override
    public void save(StoredToken token) {
        tokens.put(token.id(), token);
    }

    @Override
    public Optional<StoredToken> findById(UUID id) {
        return Optional.ofNullable(tokens.get(id));
    }

    @Override
    public Optional<StoredToken> findByLabel(String label) {
        return tokens.values().stream().filter(token -> token.label().equals(label)).findFirst();
    }

    @Override
    public List<StoredToken> findAll() {
        return List.copyOf(tokens.values());
    }

    @Override
    public void delete(UUID id) {
        tokens.remove(id);
    }
}
