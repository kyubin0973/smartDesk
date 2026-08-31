package com.smartdesk.feature.system;

import com.smartdesk.common.ApiException;
import com.smartdesk.domain.SystemAsset;
import com.smartdesk.repo.*;
import com.smartdesk.security.CurrentUser;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** REQ-F-007: 고객사별 시스템 카탈로그. REQ-E-005: 삭제는 soft delete. */
@RestController
@RequestMapping("/api")
public class SystemController {

    private final SystemAssetRepo systems;
    private final ClientRepo clients;

    public SystemController(SystemAssetRepo systems, ClientRepo clients) {
        this.systems = systems;
        this.clients = clients;
    }

    public record SystemView(Long id, Long clientId, String name, String type) {}
    public record CreateSystemRequest(@NotBlank String name, String type) {}

    @GetMapping("/clients/{clientId}/systems")
    public List<SystemView> list(@PathVariable Long clientId) {
        var p = CurrentUser.get();
        if (p.isClientUser() && !p.clientId().equals(clientId)) throw ApiException.forbidden("다른 고객사 자산은 조회할 수 없습니다.");
        return systems.findByClientIdAndActiveTrue(clientId).stream()
                .map(s -> new SystemView(s.getId(), s.getClientId(), s.getName(), s.getType()))
                .toList();
    }

    @PostMapping("/clients/{clientId}/systems")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public SystemView create(@PathVariable Long clientId, @RequestBody @jakarta.validation.Valid CreateSystemRequest req) {
        CurrentUser.requireSiUser();
        clients.findById(clientId).orElseThrow(() -> ApiException.notFound("고객사"));
        SystemAsset a = new SystemAsset();
        a.setClientId(clientId);
        a.setName(req.name().trim());
        a.setType(req.type());
        a = systems.save(a);
        return new SystemView(a.getId(), a.getClientId(), a.getName(), a.getType());
    }

    @DeleteMapping("/systems/{systemId}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long systemId) {
        CurrentUser.requireSiUser();
        SystemAsset a = systems.findById(systemId).orElseThrow(() -> ApiException.notFound("시스템"));
        a.setActive(false); // soft delete: 기존 티켓·문서 참조는 유지, 신규 선택 목록에서만 제외
        systems.save(a);
        return ResponseEntity.noContent().build();
    }
}
