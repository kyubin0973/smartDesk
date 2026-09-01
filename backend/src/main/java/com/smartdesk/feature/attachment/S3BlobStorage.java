package com.smartdesk.feature.attachment;

import com.smartdesk.common.ApiException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/** S3 (또는 MinIO 등 호환 스토리지) 저장. 다중 인스턴스용. */
public class S3BlobStorage implements BlobStorage {

    private final S3Client s3;
    private final String bucket;
    private final String prefix;

    public S3BlobStorage(S3Client s3, String bucket, String prefix) {
        this.s3 = s3;
        this.bucket = bucket;
        this.prefix = (prefix == null || prefix.isBlank()) ? "" : prefix.replaceAll("/$", "") + "/";
    }

    @Override
    public String put(String filename, InputStream data, long size, String contentType) throws IOException {
        String key = prefix + UUID.randomUUID() + "_" + safe(filename);
        try (InputStream in = data) {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket).key(key)
                            .contentType(contentType == null ? "application/octet-stream" : contentType)
                            .build(),
                    RequestBody.fromInputStream(in, size));
        }
        return key;
    }

    @Override
    public Resource get(String storageKey) {
        try {
            return new InputStreamResource(s3.getObject(GetObjectRequest.builder()
                    .bucket(bucket).key(storageKey).build()));
        } catch (NoSuchKeyException e) {
            throw ApiException.notFound("파일");
        }
    }

    @Override
    public void delete(String storageKey) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
    }

    @Override
    public boolean exists(String storageKey) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(storageKey).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    private static String safe(String name) {
        if (name == null || name.isBlank()) return "file";
        return name.replaceAll("[/\\\\:*?\"<>|]", "_");
    }
}
