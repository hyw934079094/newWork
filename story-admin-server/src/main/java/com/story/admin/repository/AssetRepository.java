package com.story.admin.repository;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetRepository extends JpaRepository<Asset, Long> {

  boolean existsByCategoryId(Long categoryId);

  @Query("select coalesce(max(a.sortOrder), -1) from Asset a where a.categoryId = :categoryId")
  Optional<Integer> findMaxSortOrderByCategoryId(@Param("categoryId") Long categoryId);

  @Query(
      """
      select a from Asset a
      where (:categoryId is null or a.categoryId = :categoryId)
        and a.status = :status
        and (:q = ''
             or lower(a.displayName) like lower(concat('%', :q, '%'))
             or lower(coalesce(a.originalFilename, '')) like lower(concat('%', :q, '%'))
             or lower(coalesce(a.description, '')) like lower(concat('%', :q, '%')))
      order by a.sortOrder asc, a.id asc
      """)
  List<Asset> search(
      @Param("categoryId") Long categoryId,
      @Param("status") AssetStatus status,
      @Param("q") String q);
}
