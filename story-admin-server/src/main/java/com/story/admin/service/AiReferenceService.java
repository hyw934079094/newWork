package com.story.admin.service;

import com.story.admin.domain.AiReferenceItem;
import com.story.admin.domain.AiReferenceSession;
import com.story.admin.dto.AiReferenceItemRequest;
import com.story.admin.repository.AiReferenceItemRepository;
import com.story.admin.repository.AiReferenceSessionRepository;
import com.story.admin.repository.AssetRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiReferenceService {

  public static final String DEFAULT_SESSION_NAME = "default";

  private final AiReferenceSessionRepository sessionRepository;
  private final AiReferenceItemRepository itemRepository;
  private final AssetRepository assetRepository;

  public AiReferenceService(
      AiReferenceSessionRepository sessionRepository,
      AiReferenceItemRepository itemRepository,
      AssetRepository assetRepository) {
    this.sessionRepository = sessionRepository;
    this.itemRepository = itemRepository;
    this.assetRepository = assetRepository;
  }

  @Transactional
  public AiReferenceSession getCurrent() {
    AiReferenceSession session = ensureDefaultSession();
    session.setItems(itemRepository.findBySessionIdOrderBySortOrderAsc(session.getId()));
    return session;
  }

  @Transactional
  public AiReferenceSession replaceCurrentItems(List<AiReferenceItemRequest> requests) {
    AiReferenceSession session = ensureDefaultSession();
    List<AiReferenceItemRequest> items = requests != null ? requests : List.of();
    for (AiReferenceItemRequest req : items) {
      Long assetId = req == null ? null : req.assetId();
      if (assetId == null || !assetRepository.existsById(assetId)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "asset not found: " + assetId);
      }
    }

    itemRepository.deleteBySessionId(session.getId());
    itemRepository.flush();

    List<AiReferenceItem> saved = new ArrayList<>();
    for (int i = 0; i < items.size(); i++) {
      AiReferenceItemRequest req = items.get(i);
      AiReferenceItem item = new AiReferenceItem();
      item.setSessionId(session.getId());
      item.setAssetId(req.assetId());
      item.setSortOrder(i);
      item.setPurpose(req.purpose());
      item.setNote(req.note());
      item.setStrength(req.strength());
      saved.add(itemRepository.save(item));
    }

    sessionRepository.save(session);
    session.setItems(saved);
    return session;
  }

  private AiReferenceSession ensureDefaultSession() {
    return sessionRepository
        .findByName(DEFAULT_SESSION_NAME)
        .orElseGet(
            () -> {
              AiReferenceSession created = new AiReferenceSession();
              created.setName(DEFAULT_SESSION_NAME);
              return sessionRepository.save(created);
            });
  }
}
