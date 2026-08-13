package com.story.admin.repository;

import com.story.admin.domain.IdentityAssetRel;
import com.story.admin.domain.IdentityAssetRelId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdentityAssetRelRepository
    extends JpaRepository<IdentityAssetRel, IdentityAssetRelId> {

  void deleteByIdentityId(Long identityId);

  void deleteByAssetId(Long assetId);

  List<IdentityAssetRel> findByIdentityId(Long identityId);

  List<IdentityAssetRel> findByAssetId(Long assetId);

  @Query(
      """
      select r.assetId from IdentityAssetRel r
      where r.identityId = :identityId
      order by r.assetId
      """)
  List<Long> findAssetIdsByIdentityId(@Param("identityId") Long identityId);

  @Query(
      """
      select i.name from IdentityAssetRel r
      join CharacterIdentity i on i.id = r.identityId
      where r.assetId = :assetId
      order by i.name
      """)
  List<String> findIdentityNamesByAssetId(@Param("assetId") Long assetId);
}