package com.applocker.enrollment;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class JpaTokenStore implements TokenStore {

    private final EnrollmentTokenJpaRepository tokens;

    JpaTokenStore(EnrollmentTokenJpaRepository tokens) {
        this.tokens = tokens;
    }

    @Override
    @Transactional
    public void save(StoredToken token) {
        EnrollmentTokenEntity entity = tokens.findById(token.id())
                .orElseGet(EnrollmentTokenEntity::new);
        entity.apply(token);
        tokens.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredToken> findById(UUID id) {
        return tokens.findById(id).map(EnrollmentTokenEntity::toStored);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredToken> findByLabel(String label) {
        return tokens.findByLabel(label).map(EnrollmentTokenEntity::toStored);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredToken> findAll() {
        return tokens.findAll().stream().map(EnrollmentTokenEntity::toStored).toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (tokens.existsById(id)) {
            tokens.deleteById(id);
        }
    }
}
