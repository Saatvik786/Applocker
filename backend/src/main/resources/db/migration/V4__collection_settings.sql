ALTER TABLE applocker_rule ADD COLUMN binary_version_low VARCHAR(40);
ALTER TABLE applocker_rule ADD COLUMN binary_version_high VARCHAR(40);

CREATE TABLE applocker_collection_setting (
    id UUID NOT NULL PRIMARY KEY,
    version_id UUID NOT NULL,
    collection_type VARCHAR(16) NOT NULL,
    enforcement_mode VARCHAR(20) NOT NULL,
    default_allow BOOLEAN NOT NULL,
    CONSTRAINT fk_collection_setting FOREIGN KEY (version_id) REFERENCES applocker_policy_version (id) ON DELETE CASCADE,
    CONSTRAINT uq_collection_setting UNIQUE (version_id, collection_type)
);
