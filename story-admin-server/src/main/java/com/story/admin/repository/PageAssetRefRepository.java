package com.story.admin.repository;

import com.story.admin.domain.PageAssetRef;
import com.story.admin.domain.PageAssetRefId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PageAssetRefRepository extends JpaRepository<PageAssetRef, PageAssetRefId> {

  List<PageAssetRef> findByPageId(Long pageId);

  void deleteByPageId(Long pageId);

  List<PageAssetRef> findByAssetId(Long assetId);

  void deleteByAssetId(Long assetId);

  @Query("select distinct r.pageId from PageAssetRef r where r.assetId = :assetId")
  List<Long> findPageIdsByAssetId(@Param("assetId") Long assetId);
}
