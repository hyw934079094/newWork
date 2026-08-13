package com.story.admin.repository;

import com.story.admin.domain.CharacterProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface CharacterProfileRepository
    extends JpaRepository<CharacterProfile, Long>, JpaSpecificationExecutor<CharacterProfile> {

  @Query("select max(c.code) from CharacterProfile c")
  Optional<String> findMaxCode();

  List<CharacterProfile> findByIdentityIdOrderByIdAsc(Long identityId);

  long countByIdentityId(Long identityId);
}