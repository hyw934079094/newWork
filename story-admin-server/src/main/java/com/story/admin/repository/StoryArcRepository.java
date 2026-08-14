package com.story.admin.repository;

import com.story.admin.domain.StoryArc;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface StoryArcRepository
    extends JpaRepository<StoryArc, Long>, JpaSpecificationExecutor<StoryArc> {

  @Query("select max(a.code) from StoryArc a")
  Optional<String> findMaxCode();

  long countBySeriesId(Long seriesId);

  List<StoryArc> findByCoverAssetId(Long coverAssetId);
}
