package com.story.admin.repository;

import com.story.admin.domain.AssetSeriesRel;
import com.story.admin.domain.AssetSeriesRelId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetSeriesRelRepository extends JpaRepository<AssetSeriesRel, AssetSeriesRelId> {

  void deleteByAssetId(Long assetId);

  @Query("select r.seriesId from AssetSeriesRel r where r.assetId = :assetId order by r.seriesId")
  List<Long> findSeriesIdsByAssetId(@Param("assetId") Long assetId);

  boolean existsByAssetId(Long assetId);

  @Query(
      """
      select distinct s.name from AssetSeriesRel r, StorySeries s
      where r.seriesId = s.id and r.assetId = :assetId
      order by s.name
      """)
  List<String> findSeriesNamesByAssetId(@Param("assetId") Long assetId);
}
