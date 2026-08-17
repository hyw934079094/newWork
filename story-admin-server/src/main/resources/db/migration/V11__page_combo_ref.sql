-- page_combo_ref: pages that reference asset combos in BEAT visual slots
CREATE TABLE page_combo_ref (
  page_id BIGINT NOT NULL,
  combo_id BIGINT NOT NULL,
  PRIMARY KEY (page_id, combo_id),
  KEY idx_page_combo_ref_combo (combo_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
