package com.story.admin.repository;

import com.story.admin.domain.AssetComboStepHold;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetComboStepHoldRepository extends JpaRepository<AssetComboStepHold, Long> {

  List<AssetComboStepHold> findByComboIdOrderByStepIndexAsc(Long comboId);

  void deleteByComboId(Long comboId);
}
