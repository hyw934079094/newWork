package com.story.admin.repository;

import com.story.admin.domain.AssetCharacterRel;
import com.story.admin.domain.AssetCharacterRelId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetCharacterRelRepository
    extends JpaRepository<AssetCharacterRel, AssetCharacterRelId> {

  void deleteByAssetId(Long assetId);

  List<AssetCharacterRel> findByAssetId(Long assetId);

  @Query(
      """
      select r.characterId from AssetCharacterRel r
      where r.assetId = :assetId
      order by r.characterId
      """)
  List<Long> findCharacterIdsByAssetId(@Param("assetId") Long assetId);

  @Query(
      """
      select r.assetId from AssetCharacterRel r
      where r.characterId = :characterId
      order by r.assetId
      """)
  List<Long> findAssetIdsByCharacterId(@Param("characterId") Long characterId);
}
