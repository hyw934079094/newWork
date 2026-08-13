package com.story.admin.service;

import com.story.admin.domain.SysConfig;
import com.story.admin.repository.SysConfigRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConfigService {

  private final SysConfigRepository repo;

  public ConfigService(SysConfigRepository repo) {
    this.repo = repo;
  }

  public String get(String key, String defaultValue) {
    return repo.findByConfigKey(key).map(SysConfig::getConfigValue).orElse(defaultValue);
  }

  public List<SysConfig> list() {
    return repo.findAll();
  }

  @Transactional
  public SysConfig create(String key, String value, String remark) {
    if (key == null || key.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "config key is required");
    }
    if (repo.existsByConfigKey(key)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "config key already exists: " + key);
    }
    SysConfig config = new SysConfig();
    config.setConfigKey(key);
    config.setConfigValue(value);
    config.setRemark(remark);
    return repo.save(config);
  }

  @Transactional
  public SysConfig upsert(String key, String value, String remark) {
    if (key == null || key.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "config key is required");
    }
    SysConfig config = repo.findByConfigKey(key).orElseGet(SysConfig::new);
    config.setConfigKey(key);
    config.setConfigValue(value);
    config.setRemark(remark);
    return repo.save(config);
  }

  @Transactional
  public void delete(String key) {
    if (!repo.existsByConfigKey(key)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "config not found: " + key);
    }
    repo.deleteByConfigKey(key);
  }
}
