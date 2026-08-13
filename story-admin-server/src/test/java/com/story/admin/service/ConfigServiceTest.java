package com.story.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.story.admin.repository.SysConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(
    properties = {
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.datasource.url=jdbc:h2:mem:story_admin_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver"
    })
class ConfigServiceTest {
  @Autowired SysConfigRepository repo;

  @Test
  void returnsDefaultWhenMissing() {
    ConfigService svc = new ConfigService(repo);
    assertThat(svc.get("storage.root", "../storage")).isEqualTo("../storage");
  }
}
