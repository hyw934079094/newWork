package com.story.admin.service;

import static com.story.admin.domain.AssetStatus.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetCategory;
import com.story.admin.domain.PageAssetRef;
import com.story.admin.domain.StoryArc;
import com.story.admin.domain.StoryPage;
import com.story.admin.dto.ArcCreateRequest;
import com.story.admin.dto.PageCreateRequest;
import com.story.admin.dto.PageUpdateRequest;
import com.story.admin.dto.SeriesCreateRequest;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetRepository;
import com.story.admin.repository.PageAssetRefRepository;
import com.story.admin.repository.StoryPageRepository;
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
      "spring.datasource.url=jdbc:h2:mem:story_admin_page_svc_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "story.storage.root=../storage"
    })
class PageServiceTest {

  @Autowired PageService pageService;
  @Autowired ArcService arcService;
  @Autowired SeriesService seriesService;
  @Autowired StoryPageRepository pageRepository;
  @Autowired PageAssetRefRepository pageAssetRefRepository;
  @Autowired AssetRepository assetRepository;
  @Autowired AssetCategoryRepository categoryRepository;

  @Test
  void createEmptyPageDefaultsContentJson() {
    Long arcId = persistArc();
    StoryPage p = pageService.create(arcId, new PageCreateRequest("P1"));
    assertThat(p.getTitle()).isEqualTo("P1");
    assertThat(p.getContentJson()).isEqualTo("[]");
    assertThat(pageService.listByArc(arcId)).extracting(StoryPage::getId).contains(p.getId());
  }

  @Test
  void saveBeatRebuildsPageAssetRef() {
    Long arcId = persistArc();
    Long assetId = persistAsset("beat-cover-ok").getId();
    StoryPage p = pageService.create(arcId, new PageCreateRequest("P1"));
    String json =
        "[{\"type\":\"BEAT\",\"coverAssetId\":"
            + assetId
            + ",\"children\":[{\"type\":\"BODY\",\"text\":\"hi\"}]}]";
    pageService.update(p.getId(), new PageUpdateRequest("P1", json));
    assertThat(pageAssetRefRepository.findByPageId(p.getId())).hasSize(1);
    PageAssetRef ref = pageAssetRefRepository.findByPageId(p.getId()).get(0);
    assertThat(ref.getAssetId()).isEqualTo(assetId);
    assertThat(ref.getRefKind()).isEqualTo("BEAT_COVER");
  }

  @Test
  void updateRejectsIllegalTopLevelType() {
    Long arcId = persistArc();
    StoryPage p = pageService.create(arcId, new PageCreateRequest("P1"));
    assertThatThrownBy(
            () ->
                pageService.update(
                    p.getId(), new PageUpdateRequest("P1", "[{\"type\":\"VIDEO\"}]")))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void deleteArcCascadesPagesAndRefs() {
    Long arcId = persistArc();
    Long assetId = persistAsset("beat-cover-cascade").getId();
    StoryPage p = pageService.create(arcId, new PageCreateRequest("P1"));
    String json =
        "[{\"type\":\"BEAT\",\"coverAssetId\":"
            + assetId
            + ",\"children\":[{\"type\":\"BODY\",\"text\":\"hi\"}]}]";
    pageService.update(p.getId(), new PageUpdateRequest("P1", json));
    assertThat(pageAssetRefRepository.findByPageId(p.getId())).hasSize(1);

    arcService.delete(arcId);

    assertThat(pageRepository.findById(p.getId())).isEmpty();
    assertThat(pageAssetRefRepository.findByPageId(p.getId())).isEmpty();
  }

  private Long persistArc() {
    Long seriesId =
        seriesService.create(new SeriesCreateRequest("测试系列", null, null, null, null)).getId();
    StoryArc arc = arcService.create(seriesId, new ArcCreateRequest("开篇", null, null, null));
    return arc.getId();
  }

  private Asset persistAsset(String name) {
    AssetCategory category = new AssetCategory();
    category.setCode("page-" + name);
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
