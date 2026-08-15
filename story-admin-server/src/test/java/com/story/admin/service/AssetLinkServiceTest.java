package com.story.admin.service;

import static com.story.admin.domain.AssetStatus.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetCategory;
import com.story.admin.domain.AssetLinkType;
import com.story.admin.domain.CharacterProfile;
import com.story.admin.domain.StorySeries;
import com.story.admin.dto.AssetUpdateRequest;
import com.story.admin.dto.CharacterCreateRequest;
import com.story.admin.dto.SeriesCreateRequest;
import com.story.admin.exception.ConflictException;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
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
      "spring.datasource.url=jdbc:h2:mem:story_admin_asset_link_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "story.storage.root=../storage"
    })
class AssetLinkServiceTest {

  @Autowired AssetService assetService;
  @Autowired CharacterService characterService;
  @Autowired SeriesService seriesService;
  @Autowired ConfigService configService;
  @Autowired AssetRepository assetRepository;
  @Autowired AssetCategoryRepository categoryRepository;

  @Test
  void updateToSeriesClearsCharacters() {
    Asset asset = persistAsset("mutex-series");
    CharacterProfile character =
        characterService.create(
            new CharacterCreateRequest(
                "互斥角色", null, null, null, null, null, null, null, null, null, null, null));
    assetService.update(
        asset.getId(),
        AssetUpdateRequest.builder().characterIds(List.of(character.getId())).build());
    assertThat(assetService.get(asset.getId()).getCharacterIds()).containsExactly(character.getId());

    StorySeries series =
        seriesService.create(new SeriesCreateRequest("互斥系列", null, null, null, null));
    Asset updated =
        assetService.update(
            asset.getId(),
            AssetUpdateRequest.builder()
                .linkType(AssetLinkType.SERIES)
                .seriesIds(List.of(series.getId()))
                .build());

    assertThat(updated.getLinkType()).isEqualTo(AssetLinkType.SERIES);
    assertThat(updated.getSeriesIds()).containsExactly(series.getId());
    assertThat(updated.getCharacterIds()).isEmpty();
    assertThat(updated.getArcIds()).isEmpty();
  }

  @Test
  void hardDeleteBlockedWhenSeriesLinked() {
    Asset asset = persistAsset("series-linked");
    StorySeries series =
        seriesService.create(new SeriesCreateRequest("暗夜关联", null, null, null, null));
    assetService.update(
        asset.getId(),
        AssetUpdateRequest.builder()
            .linkType(AssetLinkType.SERIES)
            .seriesIds(List.of(series.getId()))
            .build());

    assertThatThrownBy(() -> assetService.hardDelete(asset.getId()))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("暗夜关联");

    assertThat(assetRepository.findById(asset.getId())).isPresent();
  }

  @Test
  void uploadWithCharacterLinks(@TempDir Path dir) throws Exception {
    configService.upsert("storage.root", dir.toAbsolutePath().toString(), "test");
    AssetCategory category = persistCategory("upload-char", "上传人物关联");
    CharacterProfile character =
        characterService.create(
            new CharacterCreateRequest(
                "上传角色", null, null, null, null, null, null, null, null, null, null, null));
    MockMultipartFile file =
        new MockMultipartFile("files", "dot.png", "image/png", oneByOnePng());

    List<Asset> uploaded =
        assetService.upload(
            category.getId(),
            new MockMultipartFile[] {file},
            AssetLinkType.CHARACTER,
            null,
            null,
            List.of(character.getId()));

    assertThat(uploaded).hasSize(1);
    Asset asset = uploaded.get(0);
    assertThat(asset.getLinkType()).isEqualTo(AssetLinkType.CHARACTER);
    assertThat(asset.getCharacterIds()).containsExactly(character.getId());
    assertThat(asset.getSeriesIds()).isEmpty();
    assertThat(asset.getArcIds()).isEmpty();
  }

  @Test
  void batchLinkOverwritesAndClears() {
    AssetCategory category = persistCategory("batch-link", "批量关联");
    Asset a = persistAsset(category.getId(), "批量A");
    Asset b = persistAsset(category.getId(), "批量B");
    CharacterProfile character =
        characterService.create(
            new CharacterCreateRequest(
                "批量角色", null, null, null, null, null, null, null, null, null, null, null));
    StorySeries series =
        seriesService.create(new SeriesCreateRequest("批量系列", null, null, null, null));

    assetService.update(
        a.getId(),
        AssetUpdateRequest.builder()
            .linkType(AssetLinkType.CHARACTER)
            .characterIds(List.of(character.getId()))
            .build());

    List<Asset> linked =
        assetService.batchLink(
            List.of(a.getId(), b.getId(), a.getId()),
            AssetLinkType.SERIES,
            List.of(series.getId()),
            null,
            null);

    assertThat(linked).hasSize(2);
    assertThat(linked).allSatisfy(asset -> {
      assertThat(asset.getLinkType()).isEqualTo(AssetLinkType.SERIES);
      assertThat(asset.getSeriesIds()).containsExactly(series.getId());
      assertThat(asset.getCharacterIds()).isEmpty();
    });

    List<Asset> cleared =
        assetService.batchLink(
            List.of(a.getId(), b.getId()), AssetLinkType.NONE, null, null, null);
    assertThat(cleared).allSatisfy(asset -> {
      assertThat(asset.getLinkType()).isEqualTo(AssetLinkType.NONE);
      assertThat(asset.getSeriesIds()).isEmpty();
      assertThat(asset.getCharacterIds()).isEmpty();
      assertThat(asset.getArcIds()).isEmpty();
    });
  }

  @Test
  void batchLinkRejectsEmptyAndDeleted() {
    assertThatThrownBy(() -> assetService.batchLink(List.of(), AssetLinkType.NONE, null, null, null))
        .hasMessageContaining("assetIds");

    Asset asset = persistAsset("batch-deleted");
    assetService.recycle(asset.getId());
    assertThatThrownBy(
            () ->
                assetService.batchLink(
                    List.of(asset.getId()), AssetLinkType.NONE, null, null, null))
        .hasMessageContaining("NORMAL");
  }

  @Test
  void listByLinkTypeSeries() {
    AssetCategory category = persistCategory("list-series", "系列筛选");
    Asset unlinked = persistAsset(category.getId(), "未关联");
    Asset seriesLinked = persistAsset(category.getId(), "系列关联");
    StorySeries series =
        seriesService.create(new SeriesCreateRequest("筛选系列", null, null, null, null));
    assetService.update(
        seriesLinked.getId(),
        AssetUpdateRequest.builder()
            .linkType(AssetLinkType.SERIES)
            .seriesIds(List.of(series.getId()))
            .build());

    List<Asset> result =
        assetService.list(category.getId(), "NORMAL", null, null, null, "SERIES", null, null);

    assertThat(result).extracting(Asset::getId).containsExactly(seriesLinked.getId());
    assertThat(result).extracting(Asset::getId).doesNotContain(unlinked.getId());
    assertThat(result.get(0).getLinkType()).isEqualTo(AssetLinkType.SERIES);
  }

  private Asset persistAsset(String name) {
    return persistAsset(persistCategory("link-" + name, name).getId(), name);
  }

  private AssetCategory persistCategory(String code, String name) {
    AssetCategory category = new AssetCategory();
    category.setCode(code);
    category.setName(name);
    category.setSortOrder(1);
    category.setSystemPreset(false);
    return categoryRepository.save(category);
  }

  private Asset persistAsset(Long categoryId, String name) {
    Asset asset = new Asset();
    asset.setDisplayName(name);
    asset.setCategoryId(categoryId);
    asset.setSortOrder(0);
    asset.setStatus(NORMAL);
    asset.setStoragePath("assets/test/" + name + ".png");
    return assetRepository.save(asset);
  }

  private static byte[] oneByOnePng() throws Exception {
    BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }
}
