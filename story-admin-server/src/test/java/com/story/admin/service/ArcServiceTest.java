package com.story.admin.service;

import static com.story.admin.domain.AssetStatus.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.story.admin.domain.ArcStatus;
import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetCategory;
import com.story.admin.domain.StoryArc;
import com.story.admin.domain.StorySeries;
import com.story.admin.dto.ArcCreateRequest;
import com.story.admin.dto.ArcQuery;
import com.story.admin.dto.ArcUpdateRequest;
import com.story.admin.dto.SeriesCreateRequest;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetRepository;
import com.story.admin.repository.StoryArcRepository;
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
      "spring.datasource.url=jdbc:h2:mem:story_admin_arc_svc_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "story.storage.root=../storage"
    })
class ArcServiceTest {

  @Autowired ArcService arcService;
  @Autowired SeriesService seriesService;
  @Autowired StoryArcRepository arcRepository;
  @Autowired AssetRepository assetRepository;
  @Autowired AssetCategoryRepository categoryRepository;

  @Test
  void createRequiresTitle() {
    Long seriesId = persistSeries().getId();
    assertThatThrownBy(() -> arcService.create(seriesId, new ArcCreateRequest("  ", null, null, null)))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void createDefaultsDraft() {
    Long seriesId = persistSeries().getId();
    StoryArc a = arcService.create(seriesId, new ArcCreateRequest("开篇", null, null, null));
    assertThat(a.getCode()).startsWith("A");
    assertThat(a.getCode()).matches("A\\d{6}");
    assertThat(a.getStatus()).isEqualTo(ArcStatus.DRAFT);
  }

  @Test
  void deleteSeriesBlockedWhenArcsExist() {
    Long seriesId = persistSeries().getId();
    arcService.create(seriesId, new ArcCreateRequest("开篇", null, null, null));
    assertThatThrownBy(() -> seriesService.delete(seriesId))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            ex ->
                assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));
  }

  @Test
  void createRejectsNonNormalCoverAsset() {
    Long seriesId = persistSeries().getId();
    Asset asset = persistAsset("arc-cover-bad");
    asset.setStatus(com.story.admin.domain.AssetStatus.DELETED);
    assetRepository.save(asset);

    assertThatThrownBy(
            () ->
                arcService.create(
                    seriesId, new ArcCreateRequest("有封面", null, null, asset.getId())))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void listGetUpdateAndDeleteArc() {
    Long seriesId = persistSeries().getId();
    StoryArc created =
        arcService.create(seriesId, new ArcCreateRequest("初稿篇章", null, "简介", null));
    Asset cover = persistAsset("arc-cover-ok");

    assertThat(arcService.listBySeries(seriesId, new ArcQuery("初稿")))
        .extracting(StoryArc::getId)
        .contains(created.getId());
    assertThat(arcService.get(created.getId()).getTitle()).isEqualTo("初稿篇章");

    StoryArc updated =
        arcService.update(
            created.getId(),
            new ArcUpdateRequest("定稿篇章", ArcStatus.WRITING, "简介二", cover.getId()));
    assertThat(updated.getTitle()).isEqualTo("定稿篇章");
    assertThat(updated.getStatus()).isEqualTo(ArcStatus.WRITING);
    assertThat(updated.getCoverAssetId()).isEqualTo(cover.getId());

    arcService.delete(created.getId());
    assertThat(arcRepository.findById(created.getId())).isEmpty();
    seriesService.delete(seriesId);
    assertThat(seriesService.list(null)).extracting(StorySeries::getId).doesNotContain(seriesId);
  }

  private StorySeries persistSeries() {
    return seriesService.create(new SeriesCreateRequest("测试系列", null, null, null, null));
  }

  private Asset persistAsset(String name) {
    AssetCategory category = new AssetCategory();
    category.setCode("arc-" + name);
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
