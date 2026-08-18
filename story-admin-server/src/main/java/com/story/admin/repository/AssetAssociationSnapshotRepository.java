package com.story.admin.repository;

import com.story.admin.domain.AssetAssociationSnapshot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetAssociationSnapshotRepository
    extends JpaRepository<AssetAssociationSnapshot, Long> {

  void deleteByAssetId(Long assetId);

  List<AssetAssociationSnapshot> findByAssetIdOrderByIdAsc(Long assetId);
}
