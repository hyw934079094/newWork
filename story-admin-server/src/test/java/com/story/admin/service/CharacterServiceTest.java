package com.story.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.story.admin.domain.CharacterProfile;
import com.story.admin.dto.CharacterCreateRequest;
import com.story.admin.repository.CharacterProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(
    properties = {
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.datasource.url=jdbc:h2:mem:story_admin_char_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver"
    })
class CharacterServiceTest {
  @Autowired CharacterProfileRepository repo;

  @Test
  void createAssignsCode() {
    CharacterService characterService = new CharacterService(repo);
    CharacterProfile c =
        characterService.create(
            new CharacterCreateRequest(
                "女怪盗", null, "女", "青年", "人类", "怪盗", "公开简介", "内部说明"));
    assertThat(c.getCode()).startsWith("C");
    assertThat(c.getName()).isEqualTo("女怪盗");
  }
}
