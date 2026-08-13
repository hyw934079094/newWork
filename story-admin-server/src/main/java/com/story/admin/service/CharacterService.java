package com.story.admin.service;

import com.story.admin.domain.CharacterProfile;
import com.story.admin.dto.CharacterCreateRequest;
import com.story.admin.dto.CharacterUpdateRequest;
import com.story.admin.repository.CharacterProfileRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CharacterService {

  private final CharacterProfileRepository repo;

  public CharacterService(CharacterProfileRepository repo) {
    this.repo = repo;
  }

  public List<CharacterProfile> list() {
    return repo.findAll();
  }

  public CharacterProfile get(Long id) {
    return repo.findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "character not found: " + id));
  }

  @Transactional
  public CharacterProfile create(CharacterCreateRequest req) {
    if (req == null || req.name() == null || req.name().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "character name is required");
    }
    CharacterProfile profile = new CharacterProfile();
    profile.setCode(nextCode());
    applyFields(
        profile,
        req.name(),
        req.alias(),
        req.gender(),
        req.ageStage(),
        req.race(),
        req.occupation(),
        req.publicIntro(),
        req.internalNote());
    return repo.save(profile);
  }

  @Transactional
  public CharacterProfile update(Long id, CharacterUpdateRequest req) {
    if (req == null || req.name() == null || req.name().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "character name is required");
    }
    CharacterProfile profile = get(id);
    applyFields(
        profile,
        req.name(),
        req.alias(),
        req.gender(),
        req.ageStage(),
        req.race(),
        req.occupation(),
        req.publicIntro(),
        req.internalNote());
    return repo.save(profile);
  }

  @Transactional
  public void delete(Long id) {
    if (!repo.existsById(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "character not found: " + id);
    }
    repo.deleteById(id);
  }

  private void applyFields(
      CharacterProfile profile,
      String name,
      String alias,
      String gender,
      String ageStage,
      String race,
      String occupation,
      String publicIntro,
      String internalNote) {
    profile.setName(name.trim());
    profile.setAlias(alias);
    profile.setGender(gender);
    profile.setAgeStage(ageStage);
    profile.setRace(race);
    profile.setOccupation(occupation);
    profile.setPublicIntro(publicIntro);
    profile.setInternalNote(internalNote);
  }

  private String nextCode() {
    long next =
        repo.findMaxCode()
            .filter(code -> code != null && code.matches("C\\d+"))
            .map(code -> Long.parseLong(code.substring(1)) + 1)
            .orElse(1L);
    return String.format("C%06d", next);
  }
}
