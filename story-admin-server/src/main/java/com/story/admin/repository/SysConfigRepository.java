package com.story.admin.repository;

import com.story.admin.domain.SysConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysConfigRepository extends JpaRepository<SysConfig, Long> {
  Optional<SysConfig> findByConfigKey(String configKey);

  boolean existsByConfigKey(String configKey);

  void deleteByConfigKey(String configKey);
}
