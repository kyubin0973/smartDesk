package com.smartdesk.feature.attachment;

import com.smartdesk.common.ApiException;
import com.smartdesk.domain.Attachment;
import com.smartdesk.domain.Enums.AttachmentOwnerType;
import com.smartdesk.repo.AttachmentRepo;
import com.smartdesk.repo.DocumentRepo;
import com.smartdesk.repo.DocumentShareRepo;
import com.smartdesk.repo.TicketRepo;
import com.smartdesk.security.AuthPrincipal;
import com.smartdesk.security.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 첨부파일 (화면설계서 SCR-TICKET-001 '텍스트/이미지 첨부').
 * 저장은 {@link BlobStorage} 어댑터 — 로컬 디스크 또는 S3 (단계 4).
 */
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private static final long MAX_BYTES = 10L * 1024 * 1024; // 10MB

    private final AttachmentRepo attachments;
    private final TicketRepo tickets;
    private final DocumentRepo documents;
    private final DocumentShareRepo documentShares;
    private final BlobStorage storage;
    private final Set<String> allowedTypes;

    public AttachmentController(AttachmentRepo attachments, TicketRepo tickets, DocumentRepo documents,
                                DocumentShareRepo documentShares, BlobStorage storage,
                                @Value("${smartdesk.storage.allowed-content-types:"
                                        + "image/png,image/jpeg,image/gif,image/webp,application/pdf,"
                                        + "text/plain,text/csv,application/zip,"
                                        + "application/vnd.openxmlformats-officedocument.wordprocessingml.document,"
                                        + "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,"
                                        + "application/vnd.openxmlformats-officedocument.presentationml.presentation,"
                                        + "application/msword,application/vnd.ms-excel}") Set<String> allowedTypes) {
        this.attachments = attachments;
        this.tickets = tickets;
        this.documents = documents;
        this.documentShares = documentShares;
        this.storage = storage;
        this.allowedTypes = allowedTypes;
    }

    public record AttachmentView(Long id, String filename, String contentType, long sizeBytes, Instant createdAt) {}

    @GetMapping
    public List<AttachmentView> list(@RequestParam AttachmentOwnerType ownerType, @RequestParam Long ownerId) {
        assertCanAccess(ownerType, ownerId);
        return attachments.findByOwnerTypeAndOwnerId(ownerType, ownerId).stream()
                .map(a -> new AttachmentView(a.getId(), a.getFilename(), a.getContentType(), a.getSizeBytes(), a.getCreatedAt()))
                .toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AttachmentView upload(@RequestParam AttachmentOwnerType ownerType,
                                 @RequestParam Long ownerId,
                                 @RequestParam("file") MultipartFile file) throws IOException {
        AuthPrincipal p = assertCanAccess(ownerType, ownerId);
        if (ownerType == AttachmentOwnerType.DOCUMENT && !p.isSiUser()) {
            throw ApiException.forbidden("문서 첨부는 SI 직원만 추가할 수 있습니다.");
        }
        if (file.isEmpty()) throw ApiException.badRequest("빈 파일입니다.");
        if (file.getSize() > MAX_BYTES) throw ApiException.badRequest("파일이 10MB 를 초과합니다.");
        String contentType = file.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType.toLowerCase())) {
            throw ApiException.badRequest("허용되지 않는 파일 형식입니다: " + contentType);
        }

        String key = storage.put(file.getOriginalFilename(), file.getInputStream(),
                file.getSize(), contentType);

        Attachment a = new Attachment();
        a.setOwnerType(ownerType);
        a.setOwnerId(ownerId);
        a.setFilename(safeName(file.getOriginalFilename()));
        a.setContentType(file.getContentType());
        a.setSizeBytes(file.getSize());
        a.setStorageKey(key);
        a.setUploadedByType(p.type().name());
        a.setUploadedById(p.id());
        a = attachments.save(a);
        return new AttachmentView(a.getId(), a.getFilename(), a.getContentType(), a.getSizeBytes(), a.getCreatedAt());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) throws IOException {
        Attachment a = attachments.findById(id).orElseThrow(() -> ApiException.notFound("첨부"));
        assertCanAccess(a.getOwnerType(), a.getOwnerId());
        Resource res = storage.get(a.getStorageKey());
        return ResponseEntity.ok()
                .contentType(a.getContentType() != null ? MediaType.parseMediaType(a.getContentType()) : MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + a.getFilename() + "\"")
                .contentLength(a.getSizeBytes())
                .body(res);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Attachment a = attachments.findById(id).orElseThrow(() -> ApiException.notFound("첨부"));
        AuthPrincipal p = assertCanAccess(a.getOwnerType(), a.getOwnerId());
        boolean owner = p.type().name().equals(a.getUploadedByType()) && p.id().equals(a.getUploadedById());
        if (!owner && !p.isManager()) throw ApiException.forbidden("삭제 권한이 없습니다.");
        storage.delete(a.getStorageKey());
        attachments.delete(a);
        return ResponseEntity.noContent().build();
    }

    /** 소유 리소스(티켓/문서)에 대한 테넌시·권한 검사. */
    private AuthPrincipal assertCanAccess(AttachmentOwnerType type, Long ownerId) {
        AuthPrincipal p = CurrentUser.get();
        if (type == AttachmentOwnerType.TICKET) {
            var t = tickets.findById(ownerId).orElseThrow(() -> ApiException.notFound("티켓"));
            if (p.isClientUser() && !t.getClientId().equals(p.clientId())) throw ApiException.forbidden("접근 불가");
        } else {
            var d = documents.findById(ownerId).orElseThrow(() -> ApiException.notFound("문서"));
            if (p.isClientUser()) {
                boolean sharedToMe = d.getScope() == com.smartdesk.domain.Enums.DocScope.CLIENT_SHARED
                        && documentShares.findByDocumentId(ownerId).stream()
                                .anyMatch(s -> s.getClientId().equals(p.clientId()));
                if (!sharedToMe) throw ApiException.forbidden("접근 불가");
                // 고객사 담당자는 문서 첨부 열람만 (업로드/삭제는 컨트롤러에서 SI 만 허용하도록 별도 처리 불필요 — 아래 참고)
            }
        }
        return p;
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) return "file";
        return name.replaceAll("[/\\\\:*?\"<>|]", "_");
    }
}
