-- Character profile: story name for filtering before series entity exists

ALTER TABLE character_profile
  ADD COLUMN story_name VARCHAR(200) NULL COMMENT 'story/series name text before series entity' AFTER occupation;

CREATE INDEX idx_character_story_name ON character_profile (story_name);
