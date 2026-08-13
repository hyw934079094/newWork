package com.story.admin.repository;

import com.story.admin.domain.AssetTagRel;
import com.story.admin.domain.AssetTagRelId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetTagRelRepository extends JpaRepository<AssetTagRel, AssetTagRelId> {

  void deleteByAssetId(Long assetId);

  List<AssetTagRel> findByAssetId(Long assetId);

  @Query(
      """
      select t.name from AssetTagRel r
      join AssetTag t on t.id = r.tagId
      where r.assetId = :assetId
      order by t.name
      """)
  List<String> findTagNamesByAssetId(@Param("assetId") Long assetId);
}
