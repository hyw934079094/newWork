package com.story.admin.repository;

import com.story.admin.domain.StoryPage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoryPageRepository extends JpaRepository<StoryPage, Long> {

  List<StoryPage> findByArcIdOrderBySortOrderAscIdAsc(Long arcId);

  List<StoryPage> findByArcId(Long arcId);

  @Query("select coalesce(max(p.sortOrder), -1) from StoryPage p where p.arcId = :arcId")
  Optional<Integer> findMaxSortOrderByArcId(@Param("arcId") Long arcId);
}
