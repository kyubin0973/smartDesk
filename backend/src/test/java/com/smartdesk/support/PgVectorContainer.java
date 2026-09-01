package com.smartdesk.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트용 PostgreSQL 컨테이너 (pgvector 포함).
 * 단계 2 에서 V7 이 `CREATE EXTENSION vector` 를 하므로 기본 postgres 이미지로는 부팅 실패 →
 * pgvector/pgvector 이미지를 싱글턴으로 띄우고 @DynamicPropertySource 로 주입.
 * (TEST_DB_URL 을 주면 그 DB 를 그대로 사용 — 로컬 PostgreSQL + pgvector 설치 시.)
 */
public abstract class PgVectorContainer {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        String externalUrl = System.getenv("TEST_DB_URL");
        if (externalUrl != null && !externalUrl.isBlank()) {
            POSTGRES = null;
        } else {
            POSTGRES = new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg17")
                            .asCompatibleSubstituteFor("postgres"));
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (POSTGRES == null) return; // application-test.yml 의 TEST_DB_* 사용
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}
