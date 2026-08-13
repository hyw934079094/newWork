package com.story.admin.service;

import static com.story.admin.domain.AssetStatus.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetCategory;
import com.story.admin.domain.CharacterProfile;
import com.story.admin.dto.CharacterCreateRequest;
import com.story.admin.dto.CharacterIdentityUpsertRequest;
import com.story.admin.dto.IdentityMemberRequest;
import com.story.admin.exception.ConflictException;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetRepository;
import com.story.admin.repository.CharacterIdentityRepository;
import com.story.admin.repository.CharacterProfileRepository;
import com.story.admin.repository.IdentityAssetRelRepository;
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
      "spring.datasource.url=jdbc:h2:mem:story_admin_identity_svc_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "story.storage.root=../storage"
    })
class CharacterIdentityServiceTest {

  @Autowired CharacterIdentityService identityService;
  @Autowired CharacterService characterService;
  @Autowired CharacterIdentityRepository identityRepository;
  @Autowired CharacterProfileRepository characterProfileRepository;
  @Autowired IdentityAssetRelRepository identityAssetRelRepository;
  @Autowired AssetRepository assetRepository;
  @Autowired AssetCategoryRepository categoryRepository;

  @Test
  void deleteBlockedWhenHasForms() {
    var created =
        identityService.create(
            new CharacterIdentityUpsertRequest("怪盗女孩", "暗夜物语", null, null));
    CharacterProfile form =
        characterService.create(
            new CharacterCreateRequest(
                "日常形态甲", null, null, null, null, null, null, null, null));
    identityService.setMembers(
        created.id(), List.of(new IdentityMemberRequest(form.getId(), "日常", 1)));

    assertThatThrownBy(() -> identityService.delete(created.id()))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("日常形态甲");

    assertThat(identityRepository.findById(created.id())).isPresent();
  }

  @Test
  void createGetListUpdateMembersAssetsDeleteRoundTrip() {
    var created =
        identityService.create(
            new CharacterIdentityUpsertRequest("怪盗女孩", "暗夜物语", "公开", "内部"));
    assertThat(created.code()).startsWith("ID-");
    assertThat(created.name()).isEqualTo("怪盗女孩");

    CharacterProfile a =
        characterService.create(
            new CharacterCreateRequest("形态A", null, null, null, null, null, null, null, null));
    CharacterProfile b =
        characterService.create(
            new CharacterCreateRequest("形态B", null, null, null, null, null, null, null, null));
    identityService.setMembers(
        created.id(),
        List.of(
            new IdentityMemberRequest(a.getId(), "日常", 1),
            new IdentityMemberRequest(b.getId(), "怪盗", 2)));

    Long assetId = persistAsset("identity-shared").getId();
    var withAssets = identityService.setAssets(created.id(), List.of(assetId));
    assertThat(withAssets.assets()).extracting("assetId").containsExactly(assetId);

    var detail = identityService.get(created.id());
    assertThat(detail.members()).hasSize(2);
    assertThat(detail.members()).extracting("name").containsExactlyInAnyOrder("形态A", "形态B");
    assertThat(detail.assets()).hasSize(1);

    identityService.setMembers(
        created.id(), List.of(new IdentityMemberRequest(a.getId(), "默认", 1)));
    CharacterProfile removed = characterProfileRepository.findById(b.getId()).orElseThrow();
    assertThat(removed.getIdentityId()).isNull();
    assertThat(removed.getFormLabel()).isNull();
    CharacterProfile kept = characterProfileRepository.findById(a.getId()).orElseThrow();
    assertThat(kept.getIdentityId()).isEqualTo(created.id());
    assertThat(kept.getFormLabel()).isEqualTo("默认");

    identityService.setMembers(created.id(), List.of());
    identityService.setAssets(created.id(), List.of());
    identityService.delete(created.id());
    assertThat(identityRepository.findById(created.id())).isEmpty();
    assertThat(identityAssetRelRepository.findByIdentityId(created.id())).isEmpty();
  }

  private Asset persistAsset(String name) {
    AssetCategory category = new AssetCategory();
    category.setCode("id-" + name);
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
    asset.setContentType("image/png");
    return assetRepository.save(asset);
  }
}
