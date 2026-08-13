package com.story.admin.service;

import static com.story.admin.domain.AssetStatus.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetCategory;
import com.story.admin.domain.CharacterProfile;
import com.story.admin.dto.AssetUpdateRequest;
import com.story.admin.dto.CharacterCreateRequest;
import com.story.admin.dto.CharacterQuery;
import com.story.admin.exception.ConflictException;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetRepository;
import com.story.admin.repository.CharacterProfileRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.datasource.url=jdbc:h2:mem:story_admin_char_svc_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "story.storage.root=../storage"
    })
class CharacterServiceTest {

  @Autowired CharacterService characterService;
  @Autowired AssetService assetService;
  @Autowired CharacterProfileRepository characterProfileRepository;
  @Autowired AssetRepository assetRepository;
  @Autowired AssetCategoryRepository categoryRepository;

  @Test
  void createAssignsCode() {
    CharacterProfile c =
        characterService.create(
            new CharacterCreateRequest(
                "女怪盗", null, "女", "青年", "人类", "怪盗", "暗夜物语", "公开简介", "内部说明"));
    assertThat(c.getCode()).startsWith("C");
    assertThat(c.getName()).isEqualTo("女怪盗");
    assertThat(c.getStoryName()).isEqualTo("暗夜物语");
  }

  @Test
  void listFiltersByNameStoryAndGender() {
    characterService.create(
        new CharacterCreateRequest("女怪盗", "面具", "女", "青年", "人类", "怪盗", "暗夜物语", null, null));
    characterService.create(
        new CharacterCreateRequest("剑客阿郎", null, "男", "青年", "人类", "剑客", "江湖录", null, null));
    characterService.create(
        new CharacterCreateRequest("精灵祭司", null, "女", "成年", "精灵", "祭司", "暗夜物语", null, null));

    assertThat(characterService.list(new CharacterQuery("怪盗", null, null, null, null, null)))
        .extracting(CharacterProfile::getName)
        .containsExactly("女怪盗");
    assertThat(characterService.list(new CharacterQuery(null, "暗夜", null, null, null, null)))
        .extracting(CharacterProfile::getName)
        .containsExactlyInAnyOrder("女怪盗", "精灵祭司");
    assertThat(characterService.list(new CharacterQuery(null, null, "女", null, null, null)))
        .extracting(CharacterProfile::getName)
        .containsExactlyInAnyOrder("女怪盗", "精灵祭司");
    assertThat(characterService.list(new CharacterQuery(null, "暗夜", "女", null, "精灵", null)))
        .extracting(CharacterProfile::getName)
        .containsExactly("精灵祭司");
  }

  @Test
  void deleteBlockedWhenLinkedToAsset() {
    Long assetId = persistAsset("char-delete-linked").getId();
    CharacterProfile character =
        characterService.create(
            new CharacterCreateRequest("引用角色", null, null, null, null, null, null, null, null));
    assetService.update(
        assetId, AssetUpdateRequest.builder().characterIds(List.of(character.getId())).build());

    assertThatThrownBy(() -> characterService.delete(character.getId()))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("无法删除人物")
        .hasMessageContaining("关联素材(1)")
        .hasMessageContaining(String.valueOf(assetId));

    assertThat(characterProfileRepository.findById(character.getId())).isPresent();
  }

  private Asset persistAsset(String name) {
    AssetCategory category = new AssetCategory();
    category.setCode("char-" + name);
    category.setName(name);
    category.setSortOrder(1);
    category.setSystemPreset(false);
    category = categoryRepository.save(category);

    Asset asset = new Asset();
    asset.setDisplayName(name);
    asset.setCategoryId(category.getId());
    asset.setSortOrder(0);
    asset.setStatus(NORMAL);
    asset.setStoragePath("assets/test/" + name + ".png");
    return assetRepository.save(asset);
  }
}
