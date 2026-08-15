package com.story.admin.repository;

import com.story.admin.domain.AssetArcRel;
import com.story.admin.domain.AssetArcRelId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetArcRelRepository extends JpaRepository<AssetArcRel, AssetArcRelId> {

  void deleteByAssetId(Long assetId);

  @Query("select r.arcId from AssetArcRel r where r.assetId = :assetId order by r.arcId")
  List<Long> findArcIdsByAssetId(@Param("assetId") Long assetId);

  boolean existsByAssetId(Long assetId);

  @Query(
      """
      select distinct a.title from AssetArcRel r, StoryArc a
      where r.arcId = a.id and r.assetId = :assetId
      order by a.title
      """)
  List<String> findArcTitlesByAssetId(@Param("assetId") Long assetId);

  @Query(
      """
      select coalesce(max(r.sortOrder), -1) from AssetArcRel r, Asset a
      where r.assetId = a.id and r.arcId = :arcId and a.categoryId = :categoryId
      """)
  Optional<Integer> findMaxSortOrderByArcIdAndCategoryId(
      @Param("arcId") Long arcId, @Param("categoryId") Long categoryId);
}
