package com.story.admin.repository;

import com.story.admin.domain.AssetSeriesRel;
import com.story.admin.domain.AssetSeriesRelId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetSeriesRelRepository extends JpaRepository<AssetSeriesRel, AssetSeriesRelId> {

  void deleteByAssetId(Long assetId);

  @Query("select r.seriesId from AssetSeriesRel r where r.assetId = :assetId order by r.seriesId")
  List<Long> findSeriesIdsByAssetId(@Param("assetId") Long assetId);

  @Query(
      """
      select r.assetId, r.seriesId from AssetSeriesRel r
      where r.assetId in :assetIds
      order by r.assetId, r.seriesId
      """)
  List<Object[]> findSeriesIdsByAssetIdIn(@Param("assetIds") Collection<Long> assetIds);

  boolean existsByAssetId(Long assetId);

  @Query(
      """
      select distinct s.name from AssetSeriesRel r, StorySeries s
      where r.seriesId = s.id and r.assetId = :assetId
      order by s.name
      """)
  List<String> findSeriesNamesByAssetId(@Param("assetId") Long assetId);

  @Query(
      """
      select coalesce(max(r.sortOrder), -1) from AssetSeriesRel r, Asset a
      where r.assetId = a.id and r.seriesId = :seriesId and a.categoryId = :categoryId
      """)
  Optional<Integer> findMaxSortOrderBySeriesIdAndCategoryId(
      @Param("seriesId") Long seriesId, @Param("categoryId") Long categoryId);
}
