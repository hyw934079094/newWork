package com.story.admin.repository;

import com.story.admin.domain.AssetUnlinkedOrder;
import com.story.admin.domain.AssetUnlinkedOrderId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetUnlinkedOrderRepository
    extends JpaRepository<AssetUnlinkedOrder, AssetUnlinkedOrderId> {

  void deleteByAssetId(Long assetId);

  void deleteByCategoryIdAndAssetId(Long categoryId, Long assetId);

  Optional<AssetUnlinkedOrder> findByCategoryIdAndAssetId(Long categoryId, Long assetId);

  List<AssetUnlinkedOrder> findByCategoryIdOrderBySortOrderAscAssetIdAsc(Long categoryId);

  @Query(
      "select coalesce(max(u.sortOrder), -1) from AssetUnlinkedOrder u where u.categoryId = :categoryId")
  Optional<Integer> findMaxSortOrderByCategoryId(@Param("categoryId") Long categoryId);
}
