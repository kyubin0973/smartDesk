package com.smartdesk;

import com.smartdesk.support.PgVectorContainer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdesk.domain.Enums.AttachmentOwnerType;
import com.smartdesk.repo.AttachmentRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 첨부파일 업/다운로드 + 테넌시. 파일이 디스크에 쓰이므로 비트랜잭션 + 수동 정리. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttachmentTest extends PgVectorContainer {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AttachmentRepo attachments;
    @Value("${smartdesk.storage.dir}") String storageDir;

    private String siToken;
    private String clientAToken;
    private String clientBToken;

    @BeforeEach
    void setup() throws Exception {
        siToken = login("/api/auth/login", "admin@smartdesk.io");
        clientAToken = login("/api/auth/client-login", "user@a-corp.com");
        clientBToken = login("/api/auth/client-login", "user@b-corp.com");
    }

    @AfterEach
    void cleanup() {
        purge(AttachmentOwnerType.TICKET, 1042L);
        purge(AttachmentOwnerType.DOCUMENT, 1L);
        purge(AttachmentOwnerType.DOCUMENT, 2L);
    }

    private void purge(AttachmentOwnerType type, Long ownerId) {
        attachments.findByOwnerTypeAndOwnerId(type, ownerId).forEach(a -> {
            try { Files.deleteIfExists(Paths.get(storageDir).resolve(a.getStorageKey())); } catch (Exception ignored) {}
            attachments.delete(a);
        });
    }

    private long uploadAs(String token, String ownerType, String ownerId, String name) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", name, "text/plain", "data".getBytes());
        String body = mvc.perform(multipart("/api/attachments").file(file)
                        .param("ownerType", ownerType).param("ownerId", ownerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private String login(String path, String email) throws Exception {
        String res = mvc.perform(post(path).contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\"}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("accessToken").asText();
    }

    @Test
    void upload_list_download_delete_roundTrip() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello sla".getBytes());

        String body = mvc.perform(multipart("/api/attachments").file(file)
                        .param("ownerType", "TICKET").param("ownerId", "1042")
                        .header("Authorization", "Bearer " + siToken))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();

        mvc.perform(get("/api/attachments").param("ownerType", "TICKET").param("ownerId", "1042")
                        .header("Authorization", "Bearer " + siToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filename").value("note.txt"));

        byte[] dl = mvc.perform(get("/api/attachments/" + id).header("Authorization", "Bearer " + siToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("note.txt")))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals("hello sla", new String(dl));

        mvc.perform(delete("/api/attachments/" + id).header("Authorization", "Bearer " + siToken))
                .andExpect(status().isNoContent());
        assertTrue(attachments.findById(id).isEmpty());
    }

    @Test
    void clientUser_cannotUploadToOtherClientsTicket() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "x.txt", "text/plain", "x".getBytes());
        // 티켓 1042 = client 1. client 2(B고객사) 담당자 → RLS 로 티켓이 안 보여 404
        mvc.perform(multipart("/api/attachments").file(file)
                        .param("ownerType", "TICKET").param("ownerId", "1042")
                        .header("Authorization", "Bearer " + clientBToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void documentAttachment_siUploads_sharedClientCanRead_othersCannot() throws Exception {
        // 시드: 문서 2 = CLIENT_SHARED → client 1. 문서 1 = SI_INTERNAL.
        long id = uploadAs(siToken, "DOCUMENT", "2", "spec.txt");

        // 공유받은 고객사(A) 담당자: 목록·다운로드 가능
        mvc.perform(get("/api/attachments").param("ownerType", "DOCUMENT").param("ownerId", "2")
                        .header("Authorization", "Bearer " + clientAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filename").value("spec.txt"));
        mvc.perform(get("/api/attachments/" + id).header("Authorization", "Bearer " + clientAToken))
                .andExpect(status().isOk());

        // 공유받지 않은 고객사(B): RLS 로 문서가 안 보여 404
        mvc.perform(get("/api/attachments").param("ownerType", "DOCUMENT").param("ownerId", "2")
                        .header("Authorization", "Bearer " + clientBToken))
                .andExpect(status().isNotFound());

        // 고객사 담당자는 문서 첨부 업로드 불가
        MockMultipartFile f = new MockMultipartFile("file", "x.txt", "text/plain", "x".getBytes());
        mvc.perform(multipart("/api/attachments").file(f)
                        .param("ownerType", "DOCUMENT").param("ownerId", "2")
                        .header("Authorization", "Bearer " + clientAToken))
                .andExpect(status().isForbidden());

        // SI_INTERNAL 문서 첨부는 고객사 담당자 접근 불가 (RLS 로 문서가 안 보여 404)
        uploadAs(siToken, "DOCUMENT", "1", "internal.txt");
        mvc.perform(get("/api/attachments").param("ownerType", "DOCUMENT").param("ownerId", "1")
                        .header("Authorization", "Bearer " + clientAToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsOversizeFile() throws Exception {
        byte[] big = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "big.bin", "application/octet-stream", big);
        mvc.perform(multipart("/api/attachments").file(file)
                        .param("ownerType", "TICKET").param("ownerId", "1042")
                        .header("Authorization", "Bearer " + siToken))
                .andExpect(status().is4xxClientError());
    }
}
