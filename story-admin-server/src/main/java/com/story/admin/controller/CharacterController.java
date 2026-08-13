package com.story.admin.controller;

import com.story.admin.domain.CharacterProfile;
import com.story.admin.dto.CharacterCreateRequest;
import com.story.admin.dto.CharacterUpdateRequest;
import com.story.admin.service.CharacterService;
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
@RequestMapping("/api/characters")
public class CharacterController {

  private final CharacterService characterService;

  public CharacterController(CharacterService characterService) {
    this.characterService = characterService;
  }

  @GetMapping
  public List<CharacterProfile> list() {
    return characterService.list();
  }

  @GetMapping("/{id}")
  public CharacterProfile get(@PathVariable Long id) {
    return characterService.get(id);
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
