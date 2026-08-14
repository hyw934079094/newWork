-- story series table (ASCII comments only)
CREATE TABLE story_series (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(50) NOT NULL,
  name VARCHAR(200) NOT NULL,
  status VARCHAR(20) NOT NULL,
  cover_asset_id BIGINT NULL,
  summary TEXT NULL,
  tags VARCHAR(500) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_story_series_code (code),
  KEY idx_story_series_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
