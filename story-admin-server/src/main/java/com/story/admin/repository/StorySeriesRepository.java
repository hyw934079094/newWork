package com.story.admin.repository;

import com.story.admin.domain.StorySeries;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface StorySeriesRepository
    extends JpaRepository<StorySeries, Long>, JpaSpecificationExecutor<StorySeries> {

  @Query("select max(s.code) from StorySeries s")
  Optional<String> findMaxCode();

  List<StorySeries> findByCoverAssetId(Long coverAssetId);
}
