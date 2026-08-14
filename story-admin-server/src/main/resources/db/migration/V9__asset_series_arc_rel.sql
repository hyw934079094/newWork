CREATE TABLE asset_series_rel (
  asset_id BIGINT NOT NULL,
  series_id BIGINT NOT NULL,
  PRIMARY KEY (asset_id, series_id),
  KEY idx_asset_series_rel_series (series_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE asset_arc_rel (
  asset_id BIGINT NOT NULL,
  arc_id BIGINT NOT NULL,
  PRIMARY KEY (asset_id, arc_id),
  KEY idx_asset_arc_rel_arc (arc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
