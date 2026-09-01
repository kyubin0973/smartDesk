package com.smartdesk.unit;

import com.smartdesk.common.ApiException;
import com.smartdesk.feature.attachment.S3BlobStorage;
import org.junit.jupiter.api.*;
import org.springframework.core.io.Resource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** 단계 4: S3 스토리지 어댑터 — localstack 로 실제 왕복 검증. */
class S3BlobStorageTest {

    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.8"))
            .withServices(LocalStackContainer.Service.S3);

    static S3BlobStorage storage;

    @BeforeAll
    static void setup() {
        LOCALSTACK.start();
        S3Client s3 = S3Client.builder()
                .endpointOverride(URI.create(LOCALSTACK.getEndpoint().toString()))
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        s3.createBucket(CreateBucketRequest.builder().bucket("smartdesk-test").build());
        storage = new S3BlobStorage(s3, "smartdesk-test", "attachments");
    }

    @AfterAll
    static void stop() {
        LOCALSTACK.stop();
    }

    @Test
    void roundTrip() throws Exception {
        byte[] data = "첨부 내용".getBytes(StandardCharsets.UTF_8);
        String key = storage.put("문서.txt", new ByteArrayInputStream(data), data.length, "text/plain");

        assertTrue(key.startsWith("attachments/"));
        assertTrue(storage.exists(key));

        Resource r = storage.get(key);
        assertArrayEquals(data, r.getInputStream().readAllBytes());

        storage.delete(key);
        assertFalse(storage.exists(key));
    }

    @Test
    void get_missingKey_throwsNotFound() {
        assertThrows(ApiException.class, () -> storage.get("attachments/does-not-exist"));
    }
}
