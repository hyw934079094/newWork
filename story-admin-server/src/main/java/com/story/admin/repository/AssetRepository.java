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

  List<Asset> findAllByCategoryIdAndStatusOrderBySortOrderAsc(Long categoryId, AssetStatus status);

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
        and (
             :linkType = ''
             or (
               :linkType = 'NONE'
               and not exists (select 1 from AssetCharacterRel cr where cr.assetId = a.id)
               and not exists (select 1 from AssetSeriesRel sr where sr.assetId = a.id)
               and not exists (select 1 from AssetArcRel ar where ar.assetId = a.id)
             )
             or (
               :linkType = 'SERIES'
               and exists (
                 select 1 from AssetSeriesRel sr
                 where sr.assetId = a.id
                   and (:seriesId is null or sr.seriesId = :seriesId))
             )
             or (
               :linkType = 'ARC'
               and exists (
                 select 1 from AssetArcRel ar
                 where ar.assetId = a.id
                   and (:arcId is null or ar.arcId = :arcId)
                   and (:seriesId is null or exists (
                     select 1 from StoryArc sa where sa.id = ar.arcId and sa.seriesId = :seriesId)))
             )
             or (
               :linkType = 'CHARACTER'
               and (
                 (:characterId is not null
                   and exists (
                     select 1 from AssetCharacterRel r
                     where r.assetId = a.id and r.characterId = :characterId))
                 or (:characterId is null and :characterFilter = 'unlinked'
                   and not exists (
                     select 1 from AssetCharacterRel r where r.assetId = a.id)
                   and not exists (
                     select 1 from AssetSeriesRel sr where sr.assetId = a.id)
                   and not exists (
                     select 1 from AssetArcRel ar where ar.assetId = a.id))
                 or (:characterId is null and :characterFilter = 'all'
                   and exists (select 1 from AssetCharacterRel r where r.assetId = a.id))
               )
             )
            )
        and (
             :linkType <> ''
             or (
               (:characterId is not null
                 and exists (
                   select 1 from AssetCharacterRel r
                   where r.assetId = a.id and r.characterId = :characterId))
               or (:characterId is null and :characterFilter = 'unlinked'
                 and not exists (
                   select 1 from AssetCharacterRel r where r.assetId = a.id)
                 and not exists (
                   select 1 from AssetSeriesRel sr where sr.assetId = a.id)
                 and not exists (
                   select 1 from AssetArcRel ar where ar.assetId = a.id))
               or (:characterId is null and :characterFilter = 'all')
             )
            )
      order by a.sortOrder asc, a.id asc
      """)
  List<Asset> search(
      @Param("categoryId") Long categoryId,
      @Param("status") AssetStatus status,
      @Param("q") String q,
      @Param("characterFilter") String characterFilter,
      @Param("characterId") Long characterId,
      @Param("linkType") String linkType,
      @Param("seriesId") Long seriesId,
      @Param("arcId") Long arcId);
}
