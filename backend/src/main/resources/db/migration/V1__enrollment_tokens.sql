CREATE TABLE enrollment_token (
    id UUID NOT NULL PRIMARY KEY,
    label VARCHAR(80) NOT NULL UNIQUE,
    secret_hash VARCHAR(64) NOT NULL,
    device_limit INTEGER,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL
);

CREATE TABLE enrollment_token_device (
    token_id UUID NOT NULL,
    device_key VARCHAR(128) NOT NULL,
    PRIMARY KEY (token_id, device_key),
    CONSTRAINT fk_enrollment_token_device FOREIGN KEY (token_id) REFERENCES enrollment_token (id) ON DELETE CASCADE
);
