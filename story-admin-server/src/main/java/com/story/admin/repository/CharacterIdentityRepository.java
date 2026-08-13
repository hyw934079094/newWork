package com.story.admin.repository;

import com.story.admin.domain.CharacterIdentity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CharacterIdentityRepository extends JpaRepository<CharacterIdentity, Long> {

  boolean existsByCode(String code);

  Optional<CharacterIdentity> findByCode(String code);

  @Query("select max(c.code) from CharacterIdentity c")
  Optional<String> findMaxCode();
}