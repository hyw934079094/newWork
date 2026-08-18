package com.story.admin.repository;

import com.story.admin.domain.AssetCharacterRel;
import com.story.admin.domain.AssetCharacterRelId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetCharacterRelRepository
    extends JpaRepository<AssetCharacterRel, AssetCharacterRelId> {

  void deleteByAssetId(Long assetId);

  void deleteByCharacterId(Long characterId);

  List<AssetCharacterRel> findByAssetId(Long assetId);

  List<AssetCharacterRel> findByCharacterId(Long characterId);

  @Query(
      """
      select r.characterId from AssetCharacterRel r
      where r.assetId = :assetId
      order by r.characterId
      """)
  List<Long> findCharacterIdsByAssetId(@Param("assetId") Long assetId);

  @Query(
      """
      select r.assetId, r.characterId from AssetCharacterRel r
      where r.assetId in :assetIds
      order by r.assetId, r.characterId
      """)
  List<Object[]> findCharacterIdsByAssetIdIn(@Param("assetIds") Collection<Long> assetIds);

  @Query(
      """
      select r.assetId from AssetCharacterRel r
      where r.characterId = :characterId
      order by r.sortOrder asc, r.assetId asc
      """)
  List<Long> findAssetIdsByCharacterId(@Param("characterId") Long characterId);

  @Query(
      """
      select coalesce(max(r.sortOrder), -1) from AssetCharacterRel r, Asset a
      where r.assetId = a.id and r.characterId = :characterId and a.categoryId = :categoryId
      """)
  Optional<Integer> findMaxSortOrderByCharacterIdAndCategoryId(
      @Param("characterId") Long characterId, @Param("categoryId") Long categoryId);
}
