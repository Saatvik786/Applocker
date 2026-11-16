ALTER TABLE applocker_rule ADD COLUMN action VARCHAR(8) NOT NULL DEFAULT 'Allow';

CREATE TABLE mdm_policy_area (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE,
    base_loc_uri VARCHAR(200) NOT NULL
);

CREATE TABLE mdm_policy_area_group (
    id UUID NOT NULL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL
);

CREATE TABLE mdm_policy_area_group_mapping (
    area_id UUID NOT NULL,
    group_id UUID NOT NULL,
    PRIMARY KEY (area_id, group_id),
    CONSTRAINT fk_mdm_area_group_area FOREIGN KEY (area_id) REFERENCES mdm_policy_area (id),
    CONSTRAINT fk_mdm_area_group_group FOREIGN KEY (group_id) REFERENCES mdm_policy_area_group (id)
);

CREATE TABLE mdm_policy_definition (
    id UUID NOT NULL PRIMARY KEY,
    area_id UUID NOT NULL,
    collection_name VARCHAR(32) NOT NULL,
    xml_type VARCHAR(16) NOT NULL,
    CONSTRAINT fk_mdm_definition_area FOREIGN KEY (area_id) REFERENCES mdm_policy_area (id),
    CONSTRAINT uq_mdm_definition UNIQUE (area_id, collection_name)
);

CREATE TABLE device_group (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE
);

CREATE TABLE policy (
    id UUID NOT NULL PRIMARY KEY,
    applocker_policy_id UUID NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(400) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_policy_applocker FOREIGN KEY (applocker_policy_id) REFERENCES applocker_policy (id) ON DELETE CASCADE
);

CREATE TABLE policy_group_mapping (
    policy_id UUID NOT NULL,
    device_group_id UUID NOT NULL,
    PRIMARY KEY (policy_id, device_group_id),
    CONSTRAINT fk_policy_group_policy FOREIGN KEY (policy_id) REFERENCES policy (id) ON DELETE CASCADE,
    CONSTRAINT fk_policy_group_group FOREIGN KEY (device_group_id) REFERENCES device_group (id)
);

CREATE TABLE mdm_policy_definition_configuration (
    id UUID NOT NULL PRIMARY KEY,
    definition_id UUID NOT NULL,
    policy_id UUID NOT NULL,
    grouping_guid UUID NOT NULL,
    configuration JSONB NOT NULL,
    CONSTRAINT fk_mdm_configuration_definition FOREIGN KEY (definition_id) REFERENCES mdm_policy_definition (id),
    CONSTRAINT fk_mdm_configuration_policy FOREIGN KEY (policy_id) REFERENCES policy (id) ON DELETE CASCADE,
    CONSTRAINT uq_mdm_configuration UNIQUE (definition_id, policy_id)
);

CREATE TABLE device (
    id UUID NOT NULL PRIMARY KEY,
    windows_device_id VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    os_version VARCHAR(64),
    enrolled_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE device_mdm (
    device_id UUID NOT NULL PRIMARY KEY,
    enrollment_type VARCHAR(32),
    token_label VARCHAR(80),
    certificate_thumbprint VARCHAR(64),
    certificate_not_after TIMESTAMP WITH TIME ZONE,
    last_session_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_device_mdm FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE CASCADE
);

CREATE TABLE device_device_groups_mapping (
    device_id UUID NOT NULL,
    device_group_id UUID NOT NULL,
    PRIMARY KEY (device_id, device_group_id),
    CONSTRAINT fk_device_group_device FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE CASCADE,
    CONSTRAINT fk_device_group_group FOREIGN KEY (device_group_id) REFERENCES device_group (id)
);

INSERT INTO mdm_policy_area (id, name, base_loc_uri)
VALUES ('a1111111-1111-4111-8111-111111111111', 'AppLocker', './Vendor/MSFT/AppLocker');

INSERT INTO mdm_policy_area_group (id, code, name)
VALUES ('a2222222-2222-4222-8222-222222222222', 'APPLOCKER', 'AppLocker');

INSERT INTO mdm_policy_area_group_mapping (area_id, group_id)
VALUES ('a1111111-1111-4111-8111-111111111111', 'a2222222-2222-4222-8222-222222222222');

INSERT INTO mdm_policy_definition (id, area_id, collection_name, xml_type) VALUES
    ('a4444444-4444-4444-8444-444444444401', 'a1111111-1111-4111-8111-111111111111', 'EXE', 'Exe'),
    ('a4444444-4444-4444-8444-444444444402', 'a1111111-1111-4111-8111-111111111111', 'DLL', 'Dll'),
    ('a4444444-4444-4444-8444-444444444403', 'a1111111-1111-4111-8111-111111111111', 'MSI', 'Msi'),
    ('a4444444-4444-4444-8444-444444444404', 'a1111111-1111-4111-8111-111111111111', 'Script', 'Script'),
    ('a4444444-4444-4444-8444-444444444405', 'a1111111-1111-4111-8111-111111111111', 'StoreApps', 'Appx');

INSERT INTO device_group (id, name)
VALUES ('a3333333-3333-4333-8333-333333333333', 'Enrolled devices');
