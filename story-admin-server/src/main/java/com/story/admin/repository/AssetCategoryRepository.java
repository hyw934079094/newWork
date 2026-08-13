package com.story.admin.repository;

import com.story.admin.domain.AssetCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AssetCategoryRepository extends JpaRepository<AssetCategory, Long> {

  boolean existsByCode(String code);

  Optional<AssetCategory> findByCode(String code);

  List<AssetCategory> findAllByOrderBySortOrderAscIdAsc();

  @Query("select coalesce(max(c.sortOrder), -1) from AssetCategory c")
  Optional<Integer> findMaxSortOrder();
}
