package com.story.admin.repository;

import com.story.admin.domain.AiReferenceSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiReferenceSessionRepository extends JpaRepository<AiReferenceSession, Long> {

  Optional<AiReferenceSession> findByName(String name);
}
