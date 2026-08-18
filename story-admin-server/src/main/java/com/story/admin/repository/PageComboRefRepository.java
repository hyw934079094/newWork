package com.story.admin.repository;

import com.story.admin.domain.PageComboRef;
import com.story.admin.domain.PageComboRefId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PageComboRefRepository extends JpaRepository<PageComboRef, PageComboRefId> {

  void deleteByPageId(Long pageId);

  boolean existsByComboId(Long comboId);

  List<PageComboRef> findByComboId(Long comboId);
}
