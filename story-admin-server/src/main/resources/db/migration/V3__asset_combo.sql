-- Asset combo orchestration tables

CREATE TABLE asset_combo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    play_sequence VARCHAR(1000) NOT NULL,
    default_interval_sec DECIMAL(10, 2) NOT NULL DEFAULT 1.00,
    loop_enabled TINYINT(1) NOT NULL DEFAULT 1,
    remark VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE asset_combo_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    combo_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    member_no INT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_combo_member_no UNIQUE (combo_id, member_no),
    CONSTRAINT uk_combo_asset UNIQUE (combo_id, asset_id),
    CONSTRAINT fk_combo_member_combo FOREIGN KEY (combo_id) REFERENCES asset_combo (id),
    CONSTRAINT fk_combo_member_asset FOREIGN KEY (asset_id) REFERENCES asset (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_combo_member_combo_id ON asset_combo_member (combo_id);
CREATE INDEX idx_combo_member_asset_id ON asset_combo_member (asset_id);

CREATE TABLE asset_combo_step_hold (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    combo_id BIGINT NOT NULL,
    step_index INT NOT NULL,
    hold_seconds DECIMAL(10, 2) NOT NULL,
    CONSTRAINT uk_combo_step UNIQUE (combo_id, step_index),
    CONSTRAINT fk_combo_step_hold_combo FOREIGN KEY (combo_id) REFERENCES asset_combo (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_combo_step_hold_combo_id ON asset_combo_step_hold (combo_id);
