package com.story.admin.repository;

import com.story.admin.domain.AssetComboMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetComboMemberRepository extends JpaRepository<AssetComboMember, Long> {

  List<AssetComboMember> findByComboIdOrderBySortOrderAscMemberNoAsc(Long comboId);

  void deleteByComboId(Long comboId);

  List<AssetComboMember> findByAssetId(Long assetId);

  boolean existsByAssetId(Long assetId);

  @Query(
      """
      select distinct c.name from AssetComboMember m, AssetCombo c
      where m.comboId = c.id and m.assetId = :assetId
      order by c.name
      """)
  List<String> findComboNamesByAssetId(@Param("assetId") Long assetId);
}
