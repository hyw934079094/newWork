package com.story.admin.repository;

import com.story.admin.domain.AssetCombo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetComboRepository extends JpaRepository<AssetCombo, Long> {

  List<AssetCombo> findAllByOrderByUpdatedAtDescIdDesc();
}
