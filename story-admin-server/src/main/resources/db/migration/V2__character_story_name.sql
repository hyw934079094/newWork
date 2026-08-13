-- Character profile: story name for filtering before series entity exists

ALTER TABLE character_profile
  ADD COLUMN story_name VARCHAR(200) NULL COMMENT '@^E‹/ûð‡,ûžS1êM(	' AFTER occupation;

CREATE INDEX idx_character_story_name ON character_profile (story_name);
