package com.story.admin.controller;

import com.story.admin.domain.Asset;
import com.story.admin.domain.CharacterProfile;
import com.story.admin.dto.CharacterAssetIdsRequest;
import com.story.admin.dto.CharacterCreateRequest;
import com.story.admin.dto.CharacterQuery;
import com.story.admin.dto.CharacterUpdateRequest;
import com.story.admin.service.CharacterService;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/characters")
public class CharacterController {

  private final CharacterService characterService;

  public CharacterController(CharacterService characterService) {
    this.characterService = characterService;
  }

  @GetMapping
  public List<CharacterProfile> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String storyName,
      @RequestParam(required = false) String gender,
      @RequestParam(required = false) String ageStage,
      @RequestParam(required = false) String race,
      @RequestParam(required = false) String occupation) {
    return characterService.list(new CharacterQuery(q, storyName, gender, ageStage, race, occupation));
  }

  @GetMapping("/{id}")
  public CharacterProfile get(@PathVariable Long id) {
    return characterService.get(id);
  }

  @GetMapping("/{id}/assets")
  public List<Asset> listAssets(@PathVariable Long id) {
    return characterService.listAssets(id);
  }

  @PutMapping("/{id}/assets")
  public List<Asset> replaceAssets(
      @PathVariable Long id, @RequestBody CharacterAssetIdsRequest body) {
    return characterService.replaceAssets(id, body == null ? null : body.assetIds());
  }

  @PostMapping(value = "/{id}/assets/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public List<Asset> uploadAssets(
      @PathVariable Long id,
      @RequestParam(required = false) Long categoryId,
      @RequestParam("files") MultipartFile[] files) {
    return characterService.uploadAssets(id, categoryId, files);
  }

  @PostMapping
  public CharacterProfile create(@RequestBody CharacterCreateRequest body) {
    return characterService.create(body);
  }

  @PutMapping("/{id}")
  public CharacterProfile update(@PathVariable Long id, @RequestBody CharacterUpdateRequest body) {
    return characterService.update(id, body);
  }

  @DeleteMapping("/{id}")
  public Map<String, String> delete(@PathVariable Long id) {
    characterService.delete(id);
    return Map.of("status", "ok");
  }
}
