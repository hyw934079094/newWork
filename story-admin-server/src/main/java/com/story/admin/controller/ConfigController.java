package com.story.admin.controller;

import com.story.admin.domain.SysConfig;
import com.story.admin.service.ConfigService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/configs")
public class ConfigController {

  private final ConfigService configService;

  public ConfigController(ConfigService configService) {
    this.configService = configService;
  }

  @GetMapping
  public List<SysConfig> list() {
    return configService.list();
  }

  @PostMapping
  public SysConfig create(@RequestBody Map<String, String> body) {
    return configService.create(body.get("key"), body.get("value"), body.get("remark"));
  }

  @PutMapping("/{key}")
  public SysConfig upsert(@PathVariable String key, @RequestBody Map<String, String> body) {
    return configService.upsert(key, body.get("value"), body.get("remark"));
  }

  @DeleteMapping("/{key}")
  public Map<String, String> delete(@PathVariable String key) {
    configService.delete(key);
    return Map.of("status", "ok");
  }
}
