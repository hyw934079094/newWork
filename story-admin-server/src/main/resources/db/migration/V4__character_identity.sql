CREATE TABLE character_identity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    story_name VARCHAR(200) NULL,
    public_intro TEXT NULL,
    internal_note TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_character_identity_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE identity_asset_rel (
    identity_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    PRIMARY KEY (identity_id, asset_id),
    CONSTRAINT fk_identity_asset_rel_identity FOREIGN KEY (identity_id) REFERENCES character_identity (id),
    CONSTRAINT fk_identity_asset_rel_asset FOREIGN KEY (asset_id) REFERENCES asset (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE character_profile
  ADD COLUMN identity_id BIGINT NULL,
  ADD COLUMN form_label VARCHAR(50) NULL,
  ADD CONSTRAINT fk_character_profile_identity FOREIGN KEY (identity_id) REFERENCES character_identity (id);

CREATE INDEX idx_character_profile_identity_id ON character_profile (identity_id);
