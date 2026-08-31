package com.smartdesk.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

/** [보완] SI 직원 ↔ 담당 고객사 (REQ-F-003, REQ-F-010). */
@Entity @Table(name = "user_client")
@IdClass(UserClient.Key.class)
@Getter @Setter @NoArgsConstructor
public class UserClient {
    @Id @Column(name = "user_id")
    private Long userId;

    @Id @Column(name = "client_id")
    private Long clientId;

    public UserClient(Long userId, Long clientId) {
        this.userId = userId;
        this.clientId = clientId;
    }

    public static class Key implements Serializable {
        private Long userId;
        private Long clientId;
        public Key() {}
        @Override public boolean equals(Object o) {
            if (!(o instanceof Key k)) return false;
            return Objects.equals(userId, k.userId) && Objects.equals(clientId, k.clientId);
        }
        @Override public int hashCode() { return Objects.hash(userId, clientId); }
    }
}
