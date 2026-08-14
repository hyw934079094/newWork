-- story arc, story page, page asset ref tables (ASCII comments only)
CREATE TABLE story_arc (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  series_id BIGINT NOT NULL,
  code VARCHAR(50) NOT NULL,
  title VARCHAR(200) NOT NULL,
  summary TEXT NULL,
  status VARCHAR(20) NOT NULL,
  cover_asset_id BIGINT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_story_arc_code (code),
  KEY idx_story_arc_series (series_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE story_page (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  arc_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  content_json LONGTEXT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  KEY idx_story_page_arc (arc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE page_asset_ref (
  page_id BIGINT NOT NULL,
  asset_id BIGINT NOT NULL,
  ref_kind VARCHAR(32) NOT NULL,
  PRIMARY KEY (page_id, asset_id, ref_kind),
  KEY idx_page_asset_ref_asset (asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
