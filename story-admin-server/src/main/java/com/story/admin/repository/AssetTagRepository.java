package com.story.admin.repository;

import com.story.admin.domain.AssetTag;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetTagRepository extends JpaRepository<AssetTag, Long> {

  Optional<AssetTag> findByName(String name);

  List<AssetTag> findByNameIn(Collection<String> names);
}
