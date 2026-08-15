ALTER TABLE asset_character_rel ADD COLUMN sort_order INT NOT NULL DEFAULT 0;
ALTER TABLE asset_series_rel ADD COLUMN sort_order INT NOT NULL DEFAULT 0;
ALTER TABLE asset_arc_rel ADD COLUMN sort_order INT NOT NULL DEFAULT 0;

UPDATE asset_character_rel r
INNER JOIN asset a ON a.id = r.asset_id
SET r.sort_order = a.sort_order;

UPDATE asset_series_rel r
INNER JOIN asset a ON a.id = r.asset_id
SET r.sort_order = a.sort_order;

UPDATE asset_arc_rel r
INNER JOIN asset a ON a.id = r.asset_id
SET r.sort_order = a.sort_order;

CREATE TABLE asset_unlinked_order (
  category_id BIGINT NOT NULL,
  asset_id BIGINT NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  PRIMARY KEY (category_id, asset_id),
  CONSTRAINT fk_asset_unlinked_order_category FOREIGN KEY (category_id) REFERENCES asset_category (id),
  CONSTRAINT fk_asset_unlinked_order_asset FOREIGN KEY (asset_id) REFERENCES asset (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO asset_unlinked_order (category_id, asset_id, sort_order)
SELECT a.category_id, a.id, a.sort_order
FROM asset a
WHERE a.status = 'NORMAL'
  AND NOT EXISTS (SELECT 1 FROM asset_character_rel r WHERE r.asset_id = a.id);
