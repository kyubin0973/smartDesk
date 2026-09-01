package com.smartdesk.feature.attachment;

import com.smartdesk.common.ApiException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.UUID;

/** 로컬 디스크 저장. 단일 인스턴스용. */
public class LocalDiskStorage implements BlobStorage {

    private final Path root;

    public LocalDiskStorage(String dir) throws IOException {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    @Override
    public String put(String filename, InputStream data, long size, String contentType) throws IOException {
        String key = UUID.randomUUID() + "_" + safe(filename);
        Path target = resolve(key);
        try (InputStream in = data) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return key;
    }

    @Override
    public Resource get(String storageKey) throws IOException {
        Path path = resolve(storageKey);
        if (!Files.exists(path)) throw ApiException.notFound("파일");
        return new InputStreamResource(Files.newInputStream(path));
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIoException(e);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.exists(resolve(storageKey));
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) throw ApiException.badRequest("잘못된 저장 키입니다.");
        return p;
    }

    private static String safe(String name) {
        if (name == null || name.isBlank()) return "file";
        return name.replaceAll("[/\\\\:*?\"<>|]", "_");
    }

    static final class UncheckedIoException extends RuntimeException {
        UncheckedIoException(IOException e) { super(e); }
    }
}
