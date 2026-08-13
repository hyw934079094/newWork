package com.story.admin.repository;

import com.story.admin.domain.AiReferenceItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiReferenceItemRepository extends JpaRepository<AiReferenceItem, Long> {

  List<AiReferenceItem> findByAssetId(Long assetId);

  long countByAssetId(Long assetId);

  List<AiReferenceItem> findBySessionIdOrderBySortOrderAsc(Long sessionId);

  void deleteBySessionId(Long sessionId);
}
