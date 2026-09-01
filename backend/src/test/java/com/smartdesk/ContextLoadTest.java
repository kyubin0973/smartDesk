package com.smartdesk;

import com.smartdesk.support.PgVectorContainer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 빈 배선 + JPA 엔티티 매핑 스모크 테스트. */
@SpringBootTest
@ActiveProfiles("test")
class ContextLoadTest extends PgVectorContainer {

    @Test
    void contextLoads() {
    }
}
