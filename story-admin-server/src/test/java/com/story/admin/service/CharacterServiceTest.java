package com.story.admin.service;

import static com.story.admin.domain.AssetStatus.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetCategory;
import com.story.admin.domain.CharacterProfile;
import com.story.admin.dto.AssetUpdateRequest;
import com.story.admin.dto.CharacterAddFormRequest;
import com.story.admin.dto.CharacterCreateRequest;
import com.story.admin.dto.CharacterIdentityUpsertRequest;
import com.story.admin.dto.CharacterQuery;
import com.story.admin.dto.CharacterUpdateRequest;
import com.story.admin.dto.IdentityDetailResponse;
import com.story.admin.dto.IdentityMemberRequest;
import com.story.admin.exception.ConflictException;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetRepository;
import com.story.admin.repository.CharacterIdentityRepository;
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
  @Autowired CharacterIdentityService identityService;
  @Autowired AssetService assetService;
  @Autowired CharacterProfileRepository characterProfileRepository;
  @Autowired CharacterIdentityRepository identityRepository;
  @Autowired AssetRepository assetRepository;
  @Autowired AssetCategoryRepository categoryRepository;

  @Test
  void createAssignsCode() {
    CharacterProfile c =
        characterService.create(
            new CharacterCreateRequest(
                "女怪盗", null, "女", "青年", "人类", "怪盗", "暗夜物语", "公开简介", "内部说明", null, null));
    assertThat(c.getCode()).startsWith("C");
    assertThat(c.getName()).isEqualTo("女怪盗");
    assertThat(c.getStoryName()).isEqualTo("暗夜物语");
  }

  @Test
  void listFiltersByNameStoryAndGender() {
    characterService.create(
        new CharacterCreateRequest(
            "女怪盗", "面具", "女", "青年", "人类", "怪盗", "暗夜物语", null, null, null, null));
    characterService.create(
        new CharacterCreateRequest(
            "剑客阿郎", null, "男", "青年", "人类", "剑客", "江湖录", null, null, null, null));
    characterService.create(
        new CharacterCreateRequest(
            "精灵祭司", null, "女", "成年", "精灵", "祭司", "暗夜物语", null, null, null, null));

    assertThat(characterService.list(new CharacterQuery("怪盗", null, null, null, null, null, null)))
        .extracting(CharacterProfile::getName)
        .containsExactly("女怪盗");
    assertThat(characterService.list(new CharacterQuery(null, "暗夜", null, null, null, null, null)))
        .extracting(CharacterProfile::getName)
        .containsExactlyInAnyOrder("女怪盗", "精灵祭司");
    assertThat(characterService.list(new CharacterQuery(null, null, "女", null, null, null, null)))
        .extracting(CharacterProfile::getName)
        .containsExactlyInAnyOrder("女怪盗", "精灵祭司");
    assertThat(
            characterService.list(new CharacterQuery(null, "暗夜", "女", null, "精灵", null, null)))
        .extracting(CharacterProfile::getName)
        .containsExactly("精灵祭司");
  }

  @Test
  void deleteBlockedWhenLinkedToAsset() {
    Long assetId = persistAsset("char-delete-linked").getId();
    CharacterProfile character =
        characterService.create(
            new CharacterCreateRequest(
                "引用角色", null, null, null, null, null, null, null, null, null, null));
    assetService.update(
        assetId, AssetUpdateRequest.builder().characterIds(List.of(character.getId())).build());

    assertThatThrownBy(() -> characterService.delete(character.getId()))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("无法删除人物")
        .hasMessageContaining("关联素材(1)")
        .hasMessageContaining(String.valueOf(assetId));

    assertThat(characterProfileRepository.findById(character.getId())).isPresent();
  }

  @Test
  void addFormCreatesIdentityForStandaloneCharacter() {
    CharacterProfile original =
        characterService.create(
            new CharacterCreateRequest(
                "女怪盗", null, "女", "青年", "人类", "怪盗", "暗夜物语", null, null, null, null));
    assertThat(original.getIdentityId()).isNull();

    IdentityDetailResponse detail =
        characterService.addForm(
            original.getId(),
            new CharacterAddFormRequest(
                "怪盗女孩",
                "日常",
                new CharacterCreateRequest(
                    "怪盗××", null, null, null, null, null, null, null, null, null, "怪盗")));

    assertThat(detail.code()).startsWith("ID-");
    assertThat(detail.name()).isEqualTo("怪盗女孩");
    assertThat(detail.members()).hasSize(2);
    assertThat(detail.members())
        .extracting(IdentityDetailResponse.MemberView::name)
        .containsExactlyInAnyOrder("女怪盗", "怪盗××");
    assertThat(detail.members())
        .extracting(IdentityDetailResponse.MemberView::formLabel)
        .containsExactlyInAnyOrder("日常", "怪盗");

    CharacterProfile refreshedOriginal =
        characterProfileRepository.findById(original.getId()).orElseThrow();
    Long newId =
        detail.members().stream()
            .filter(m -> "怪盗××".equals(m.name()))
            .findFirst()
            .orElseThrow()
            .characterId();
    CharacterProfile created = characterProfileRepository.findById(newId).orElseThrow();
    assertThat(refreshedOriginal.getIdentityId()).isEqualTo(detail.id());
    assertThat(created.getIdentityId()).isEqualTo(detail.id());
    assertThat(refreshedOriginal.getFormLabel()).isEqualTo("日常");
    assertThat(created.getFormLabel()).isEqualTo("怪盗");
    assertThat(identityRepository.count()).isEqualTo(1);
  }

  @Test
  void updatePreservesIdentityWhenOmitted() {
    CharacterProfile character =
        characterService.create(
            new CharacterCreateRequest(
                "日常形态", null, null, null, null, null, "暗夜物语", null, null, null, null));
    var identity =
        identityService.create(
            new CharacterIdentityUpsertRequest("怪盗女孩", "暗夜物语", null, null));
    identityService.setMembers(
        identity.id(), List.of(new IdentityMemberRequest(character.getId(), "日常", 1)));

    CharacterProfile updated =
        characterService.update(
            character.getId(),
            new CharacterUpdateRequest(
                "改名后", null, null, null, null, null, "暗夜物语", null, null, null, null));

    assertThat(updated.getName()).isEqualTo("改名后");
    assertThat(updated.getIdentityId()).isEqualTo(identity.id());
    assertThat(updated.getFormLabel()).isEqualTo("日常");
  }

  @Test
  void addFormReusesExistingIdentity() {
    CharacterProfile original =
        characterService.create(
            new CharacterCreateRequest(
                "日常形态", null, null, null, null, null, "暗夜物语", null, null, null, null));
    var identity =
        identityService.create(
            new CharacterIdentityUpsertRequest("怪盗女孩", "暗夜物语", null, null));
    identityService.setMembers(
        identity.id(), List.of(new IdentityMemberRequest(original.getId(), "日常", 1)));
    long identityCountBefore = identityRepository.count();

    IdentityDetailResponse detail =
        characterService.addForm(
            original.getId(),
            new CharacterAddFormRequest(
                "应被忽略的本体名",
                "应不改原标签",
                new CharacterCreateRequest(
                    "怪盗形态", null, null, null, null, null, null, null, null, null, "怪盗")));

    assertThat(identityRepository.count()).isEqualTo(identityCountBefore);
    assertThat(detail.id()).isEqualTo(identity.id());
    assertThat(detail.name()).isEqualTo("怪盗女孩");
    assertThat(detail.members()).hasSize(2);
    assertThat(detail.members())
        .extracting(IdentityDetailResponse.MemberView::name)
        .containsExactlyInAnyOrder("日常形态", "怪盗形态");

    CharacterProfile refreshedOriginal =
        characterProfileRepository.findById(original.getId()).orElseThrow();
    assertThat(refreshedOriginal.getFormLabel()).isEqualTo("日常");
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
