package com.smartdesk.feature.attachment;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/** 단계 4: 첨부파일 저장소 추상화. 로컬 디스크 ↔ S3 를 설정으로 교체 (다중 인스턴스 필수). */
public interface BlobStorage {

    /** 저장 후 조회에 쓸 storageKey 반환. */
    String put(String filename, InputStream data, long size, String contentType) throws IOException;

    Resource get(String storageKey) throws IOException;

    void delete(String storageKey);

    boolean exists(String storageKey);
}
