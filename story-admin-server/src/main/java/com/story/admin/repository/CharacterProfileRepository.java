package com.story.admin.repository;

import com.story.admin.domain.CharacterProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CharacterProfileRepository extends JpaRepository<CharacterProfile, Long> {

  @Query("select max(c.code) from CharacterProfile c")
  Optional<String> findMaxCode();
}
