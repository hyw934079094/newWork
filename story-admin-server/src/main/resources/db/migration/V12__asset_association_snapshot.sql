CREATE TABLE asset_association_snapshot (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  asset_id BIGINT NOT NULL,
  kind VARCHAR(64) NOT NULL,
  payload_json TEXT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  KEY idx_asset_association_snapshot_asset (asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
