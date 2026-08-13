package com.story.admin.controller;

import com.story.admin.dto.CharacterIdentityUpsertRequest;
import com.story.admin.dto.IdentityAssetIdsRequest;
import com.story.admin.dto.IdentityDetailResponse;
import com.story.admin.dto.IdentityMemberRequest;
import com.story.admin.service.CharacterIdentityService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/character-identities")
public class CharacterIdentityController {

  private final CharacterIdentityService identityService;

  public CharacterIdentityController(CharacterIdentityService identityService) {
    this.identityService = identityService;
  }

  @GetMapping
  public List<IdentityDetailResponse> list() {
    return identityService.list();
  }

  @PostMapping
  public IdentityDetailResponse create(@RequestBody CharacterIdentityUpsertRequest body) {
    return identityService.create(body);
  }

  @GetMapping("/{id}")
  public IdentityDetailResponse get(@PathVariable Long id) {
    return identityService.get(id);
  }

  @PutMapping("/{id}")
  public IdentityDetailResponse update(
      @PathVariable Long id, @RequestBody CharacterIdentityUpsertRequest body) {
    return identityService.update(id, body);
  }

  @DeleteMapping("/{id}")
  public Map<String, String> delete(@PathVariable Long id) {
    identityService.delete(id);
    return Map.of("status", "ok");
  }

  @PutMapping("/{id}/members")
  public IdentityDetailResponse setMembers(
      @PathVariable Long id, @RequestBody List<IdentityMemberRequest> members) {
    return identityService.setMembers(id, members);
  }

  @PutMapping("/{id}/assets")
  public IdentityDetailResponse setAssets(
      @PathVariable Long id, @RequestBody IdentityAssetIdsRequest body) {
    return identityService.setAssets(id, body == null ? null : body.assetIds());
  }
}
