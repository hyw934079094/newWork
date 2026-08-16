package com.story.admin.service;

import static com.story.admin.domain.AssetStatus.DELETED;
import static com.story.admin.domain.AssetStatus.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetCategory;
import com.story.admin.dto.ComboMemberRequest;
import com.story.admin.dto.ComboStepHoldRequest;
import com.story.admin.dto.ComboUpsertRequest;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetComboMemberRepository;
import com.story.admin.repository.AssetComboRepository;
import com.story.admin.repository.AssetComboStepHoldRepository;
import com.story.admin.repository.AssetRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.datasource.url=jdbc:h2:mem:story_admin_combo_svc_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "story.storage.root=../storage"
    })
class ComboServiceTest {

  @Autowired ComboService comboService;
  @Autowired AssetRepository assetRepository;
  @Autowired AssetCategoryRepository categoryRepository;
  @Autowired AssetComboRepository comboRepository;
  @Autowired AssetComboMemberRepository memberRepository;
  @Autowired AssetComboStepHoldRepository stepHoldRepository;

  @Test
  void rejectsNonNormalMemberAsset() {
    Asset deleted = persistAsset("combo-deleted");
    deleted.setStatus(DELETED);
    deleted = assetRepository.save(deleted);

    ComboUpsertRequest req =
        new ComboUpsertRequest(
            "deleted-member",
            "1",
            new BigDecimal("1.0"),
            true,
            null,
            List.of(new ComboMemberRequest(deleted.getId(), 1)),
            List.of());

    assertThatThrownBy(() -> comboService.create(req))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            ex -> {
              ResponseStatusException rse = (ResponseStatusException) ex;
              assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(rse.getReason()).contains("asset is not available");
            });
  }

  @Test
  void rejectsPlaySequenceWithUnknownMemberNo() {
    Long a1 = persistAsset("combo-m1").getId();
    Long a2 = persistAsset("combo-m2").getId();

    ComboUpsertRequest req =
        new ComboUpsertRequest(
            "seq-bad",
            "1,3",
            new BigDecimal("1.0"),
            true,
            null,
            List.of(new ComboMemberRequest(a1, 1), new ComboMemberRequest(a2, 2)),
            List.of());

    assertThatThrownBy(() -> comboService.create(req))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            ex -> {
              ResponseStatusException rse = (ResponseStatusException) ex;
              assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
  }

  @Test
  void rejectsStepHoldOutOfRange() {
    Long a1 = persistAsset("combo-h1").getId();
    Long a2 = persistAsset("combo-h2").getId();

    ComboUpsertRequest req =
        new ComboUpsertRequest(
            "hold-bad",
            "1,2,1",
            new BigDecimal("1.0"),
            true,
            null,
            List.of(new ComboMemberRequest(a1, 1), new ComboMemberRequest(a2, 2)),
            List.of(new ComboStepHoldRequest(4, new BigDecimal("2.0"))));

    assertThatThrownBy(() -> comboService.create(req))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            ex -> {
              ResponseStatusException rse = (ResponseStatusException) ex;
              assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
  }

  @Test
  void createGetListUpdateDeleteRoundTrip() {
    Long a1 = persistAsset("combo-crud-1").getId();
    Long a2 = persistAsset("combo-crud-2").getId();
    Long a3 = persistAsset("combo-crud-3").getId();

    var created =
        comboService.create(
            new ComboUpsertRequest(
                "表情循环A",
                "1,2,1",
                new BigDecimal("1.0"),
                true,
                "remark",
                List.of(new ComboMemberRequest(a1, 1), new ComboMemberRequest(a2, 2)),
                List.of(new ComboStepHoldRequest(2, new BigDecimal("2.0")))));

    assertThat(created.id()).isNotNull();
    assertThat(created.members()).hasSize(2);
    assertThat(created.members().get(0).displayName()).isEqualTo("combo-crud-1");
    assertThat(created.members().get(0).contentUrl()).isEqualTo("/api/assets/" + a1 + "/content");
    assertThat(created.stepHolds()).hasSize(1);

    var fetched = comboService.get(created.id());
    assertThat(fetched.name()).isEqualTo("表情循环A");
    assertThat(comboService.list()).extracting(r -> r.id()).contains(created.id());

    var updated =
        comboService.update(
            created.id(),
            new ComboUpsertRequest(
                "表情循环B",
                "1,3,2",
                new BigDecimal("0.5"),
                false,
                null,
                List.of(
                    new ComboMemberRequest(a1, null),
                    new ComboMemberRequest(a3, null),
                    new ComboMemberRequest(a2, null)),
                List.of()));

    assertThat(updated.name()).isEqualTo("表情循环B");
    assertThat(updated.loopEnabled()).isFalse();
    assertThat(updated.members()).extracting(m -> m.memberNo()).containsExactly(1, 2, 3);
    assertThat(updated.members()).extracting(m -> m.assetId()).containsExactly(a1, a3, a2);
    assertThat(updated.stepHolds()).isEmpty();
    assertThat(memberRepository.findByComboIdOrderBySortOrderAscMemberNoAsc(created.id()))
        .hasSize(3);
    assertThat(stepHoldRepository.findByComboIdOrderByStepIndexAsc(created.id())).isEmpty();

    comboService.delete(created.id());
    assertThat(comboRepository.findById(created.id())).isEmpty();
    assertThat(memberRepository.findByComboIdOrderBySortOrderAscMemberNoAsc(created.id())).isEmpty();
    assertThat(assetRepository.findById(a1)).isPresent();
  }

  @Test
  void emptyPlaySequenceDefaultsToMemberOrder() {
    Long a1 = persistAsset("combo-def-1").getId();
    Long a2 = persistAsset("combo-def-2").getId();

    var created =
        comboService.create(
            new ComboUpsertRequest(
                "default-seq",
                "  ",
                new BigDecimal("1.0"),
                true,
                null,
                List.of(new ComboMemberRequest(a1, 1), new ComboMemberRequest(a2, 2)),
                List.of()));

    assertThat(created.playSequence()).isEqualTo("1,2");
  }

  private Asset persistAsset(String name) {
    AssetCategory category = new AssetCategory();
    category.setCode("combo-" + name);
    category.setName(name);
    category.setSortOrder(0);
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
