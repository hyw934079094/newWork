package com.story.admin.service;

import com.story.admin.domain.AiReferenceItem;
import com.story.admin.domain.AiReferenceSession;
import com.story.admin.dto.AiReferenceItemRequest;
import com.story.admin.repository.AiReferenceItemRepository;
import com.story.admin.repository.AiReferenceSessionRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiReferenceService {

  public static final String DEFAULT_SESSION_NAME = "default";

  private final AiReferenceSessionRepository sessionRepository;
  private final AiReferenceItemRepository itemRepository;

  public AiReferenceService(
      AiReferenceSessionRepository sessionRepository, AiReferenceItemRepository itemRepository) {
    this.sessionRepository = sessionRepository;
    this.itemRepository = itemRepository;
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
    itemRepository.deleteBySessionId(session.getId());
    itemRepository.flush();

    List<AiReferenceItem> saved = new ArrayList<>();
    List<AiReferenceItemRequest> items = requests != null ? requests : List.of();
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
