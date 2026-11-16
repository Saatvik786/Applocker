CREATE TABLE applocker_policy (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE,
    description VARCHAR(400) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE applocker_policy_version (
    id UUID NOT NULL PRIMARY KEY,
    policy_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    enforcement_mode VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_applocker_policy_version FOREIGN KEY (policy_id) REFERENCES applocker_policy (id) ON DELETE CASCADE,
    CONSTRAINT uq_applocker_policy_version UNIQUE (policy_id, version_number)
);

CREATE TABLE applocker_rule (
    id UUID NOT NULL PRIMARY KEY,
    version_id UUID NOT NULL,
    collection_type VARCHAR(16) NOT NULL,
    rule_kind VARCHAR(16) NOT NULL,
    name VARCHAR(120) NOT NULL,
    user_or_group_sid VARCHAR(64) NOT NULL,
    path VARCHAR(260),
    publisher_name VARCHAR(300),
    product_name VARCHAR(200),
    binary_name VARCHAR(200),
    hash_data VARCHAR(200),
    source_file_name VARCHAR(260),
    source_file_length BIGINT,
    sort_order INTEGER NOT NULL,
    CONSTRAINT fk_applocker_rule FOREIGN KEY (version_id) REFERENCES applocker_policy_version (id) ON DELETE CASCADE
);
