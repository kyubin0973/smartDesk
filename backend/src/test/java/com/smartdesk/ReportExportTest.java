package com.smartdesk;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 0.5-d: 감사 로그 CSV 내보내기 + SLA 준수율 리포트. */
class ReportExportTest extends AbstractIntegrationTest {

    @Test
    void auditCsvExport_managerOnly() throws Exception {
        mvc.perform(get("/api/audit/export").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());

        String csv = mvc.perform(get("/api/audit/export?action=LOGIN_SUCCESS")
                        .header("Authorization", "Bearer " + siToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".csv")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("﻿"), "Excel 한글 인식용 BOM");
        assertTrue(csv.contains("액션"), "헤더 행");
        assertTrue(csv.contains("LOGIN_SUCCESS"));
    }

    @Test
    void ticketEventCsvExport() throws Exception {
        String csv = mvc.perform(get("/api/audit/ticket-events/export")
                        .header("Authorization", "Bearer " + siToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("이벤트"));
    }

    @Test
    void slaReport_structure_managerOnly() throws Exception {
        mvc.perform(get("/api/reports/sla").header("Authorization", "Bearer " + clientAToken))
                .andExpect(status().isForbidden());

        JsonNode r = tree(mvc.perform(get("/api/reports/sla").header("Authorization", "Bearer " + siToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(r.has("total"));
        assertTrue(r.get("complianceRate").isNumber());
        assertTrue(r.get("byCategory").isArray());
        assertTrue(r.get("byClient").isArray());
        assertEquals(r.get("total").asInt(), r.get("met").asInt() + r.get("breached").asInt());
    }

    @Test
    void slaReport_badDate_is400() throws Exception {
        mvc.perform(get("/api/reports/sla?from=nope").header("Authorization", "Bearer " + siToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void slaReportCsvExport() throws Exception {
        String csv = mvc.perform(get("/api/reports/sla/export").header("Authorization", "Bearer " + siToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("준수율(%)"));
        assertTrue(csv.contains("전체"));
    }
}
