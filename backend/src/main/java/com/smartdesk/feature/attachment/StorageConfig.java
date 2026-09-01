package com.smartdesk.feature.attachment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.io.IOException;
import java.net.URI;

/** 단계 4: smartdesk.storage.type = local | s3. */
@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    public BlobStorage blobStorage(
            @Value("${smartdesk.storage.type:local}") String type,
            @Value("${smartdesk.storage.dir:./var/attachments}") String dir,
            @Value("${smartdesk.storage.s3.bucket:}") String bucket,
            @Value("${smartdesk.storage.s3.region:us-east-1}") String region,
            @Value("${smartdesk.storage.s3.endpoint:}") String endpoint,
            @Value("${smartdesk.storage.s3.prefix:attachments}") String prefix,
            @Value("${smartdesk.storage.s3.access-key:}") String accessKey,
            @Value("${smartdesk.storage.s3.secret-key:}") String secretKey) throws IOException {

        if (!"s3".equalsIgnoreCase(type)) {
            log.info("[storage] 로컬 디스크: {}", dir);
            return new LocalDiskStorage(dir);
        }

        var b = S3Client.builder().region(Region.of(region));
        if (!endpoint.isBlank()) {
            b.endpointOverride(URI.create(endpoint))
             .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        b.credentialsProvider(accessKey.isBlank()
                ? DefaultCredentialsProvider.create()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        log.info("[storage] S3: bucket={} region={} endpoint={}", bucket, region,
                endpoint.isBlank() ? "(aws)" : endpoint);
        return new S3BlobStorage(b.build(), bucket, prefix);
    }
}
