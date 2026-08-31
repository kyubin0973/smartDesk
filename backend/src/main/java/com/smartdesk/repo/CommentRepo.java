package com.smartdesk.repo;

import com.smartdesk.domain.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepo extends JpaRepository<Comment, Long> {
    List<Comment> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
