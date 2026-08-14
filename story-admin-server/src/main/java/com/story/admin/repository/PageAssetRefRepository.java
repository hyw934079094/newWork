package com.story.admin.repository;

import com.story.admin.domain.PageAssetRef;
import com.story.admin.domain.PageAssetRefId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PageAssetRefRepository extends JpaRepository<PageAssetRef, PageAssetRefId> {

  List<PageAssetRef> findByPageId(Long pageId);

  void deleteByPageId(Long pageId);
}
