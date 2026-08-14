package com.story.admin.service;

import static com.story.admin.domain.AssetStatus.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetCategory;
import com.story.admin.domain.SeriesStatus;
import com.story.admin.domain.StorySeries;
import com.story.admin.dto.SeriesCreateRequest;
import com.story.admin.dto.SeriesQuery;
import com.story.admin.dto.SeriesUpdateRequest;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetRepository;
import com.story.admin.repository.StorySeriesRepository;
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
      "spring.datasource.url=jdbc:h2:mem:story_admin_series_svc_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "story.storage.root=../storage"
    })
class SeriesServiceTest {

  @Autowired SeriesService seriesService;
  @Autowired StorySeriesRepository seriesRepository;
  @Autowired AssetRepository assetRepository;
  @Autowired AssetCategoryRepository categoryRepository;

  @Test
  void createRequiresName() {
    assertThatThrownBy(
            () -> seriesService.create(new SeriesCreateRequest("  ", null, null, null, null)))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void createDefaultsDraftAndListByQ() {
    StorySeries s =
        seriesService.create(
            new SeriesCreateRequest("暗夜物语", null, "简介", "奇幻,连载", null));
    assertThat(s.getCode()).startsWith("S");
    assertThat(s.getStatus()).isEqualTo(SeriesStatus.DRAFT);
    assertThat(seriesService.list(new SeriesQuery("暗夜", null)))
        .extracting(StorySeries::getId)
        .contains(s.getId());
  }

  @Test
  void createRejectsNonNormalCoverAsset() {
    Asset asset = persistAsset("cover-bad");
    asset.setStatus(com.story.admin.domain.AssetStatus.DELETED);
    assetRepository.save(asset);

    assertThatThrownBy(
            () ->
                seriesService.create(
                    new SeriesCreateRequest("有封面", null, null, null, asset.getId())))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void updateAndDeleteSeries() {
    StorySeries created =
        seriesService.create(new SeriesCreateRequest("初稿系列", null, null, null, null));
    Asset cover = persistAsset("cover-ok");

    StorySeries updated =
        seriesService.update(
            created.getId(),
            new SeriesUpdateRequest(
                "定稿系列", SeriesStatus.SERIALIZING, "简介二", "科幻", cover.getId()));
    assertThat(updated.getName()).isEqualTo("定稿系列");
    assertThat(updated.getStatus()).isEqualTo(SeriesStatus.SERIALIZING);
    assertThat(updated.getCoverAssetId()).isEqualTo(cover.getId());

    seriesService.delete(created.getId());
    assertThat(seriesRepository.findById(created.getId())).isEmpty();
  }

  private Asset persistAsset(String name) {
    AssetCategory category = new AssetCategory();
    category.setCode("series-" + name);
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
