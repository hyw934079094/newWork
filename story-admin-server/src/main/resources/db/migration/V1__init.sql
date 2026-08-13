-- Asset module initial schema

CREATE TABLE asset_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    system_preset TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_asset_category_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE asset (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    category_id BIGINT NOT NULL,
    series_id BIGINT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    description TEXT NULL,
    original_filename VARCHAR(500) NULL,
    storage_path VARCHAR(1000) NOT NULL,
    content_type VARCHAR(100) NULL,
    width INT NULL,
    height INT NULL,
    size_bytes BIGINT NULL,
    checksum VARCHAR(64) NULL,
    chapter_ref_placeholder VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    CONSTRAINT fk_asset_category FOREIGN KEY (category_id) REFERENCES asset_category (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_asset_category_id ON asset (category_id);
CREATE INDEX idx_asset_status ON asset (status);

CREATE TABLE asset_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uk_asset_tag_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE asset_tag_rel (
    asset_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (asset_id, tag_id),
    CONSTRAINT fk_asset_tag_rel_asset FOREIGN KEY (asset_id) REFERENCES asset (id),
    CONSTRAINT fk_asset_tag_rel_tag FOREIGN KEY (tag_id) REFERENCES asset_tag (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE character_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    alias VARCHAR(200) NULL,
    gender VARCHAR(20) NULL,
    age_stage VARCHAR(50) NULL,
    race VARCHAR(50) NULL,
    occupation VARCHAR(100) NULL,
    public_intro TEXT NULL,
    internal_note TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_character_profile_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE asset_character_rel (
    asset_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    PRIMARY KEY (asset_id, character_id),
    CONSTRAINT fk_asset_character_rel_asset FOREIGN KEY (asset_id) REFERENCES asset (id),
    CONSTRAINT fk_asset_character_rel_character FOREIGN KEY (character_id) REFERENCES character_profile (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_reference_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_reference_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    purpose VARCHAR(255) NULL,
    note TEXT NULL,
    strength DECIMAL(5, 2) NULL,
    CONSTRAINT fk_ai_reference_item_session FOREIGN KEY (session_id) REFERENCES ai_reference_session (id),
    CONSTRAINT fk_ai_reference_item_asset FOREIGN KEY (asset_id) REFERENCES asset (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_ai_reference_item_session_id ON ai_reference_item (session_id);

CREATE TABLE sys_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL,
    config_value TEXT NULL,
    remark VARCHAR(500) NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_sys_config_key UNIQUE (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO asset_category (code, name, sort_order, system_preset, created_at, updated_at) VALUES
('expression', '人物表情', 1, 1, NOW(), NOW()),
('portrait', '人物立绘', 2, 1, NOW(), NOW()),
('costume', '人物服装', 3, 1, NOW(), NOW()),
('mixed', '综合素材', 4, 1, NOW(), NOW()),
('complete', '完整图片', 5, 1, NOW(), NOW());
