package com.smartdesk;

import com.smartdesk.domain.Contract;
import com.smartdesk.domain.Enums.ContractStatus;
import com.smartdesk.feature.contract.ContractStatusService;
import com.smartdesk.repo.ContractRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A3 이후 분리된 계약 상태 전이 로직. */
class ContractStatusServiceTest extends AbstractIntegrationTest {

    @Autowired ContractStatusService service;
    @Autowired ContractRepo contracts;

    private Contract contractEndingOn(LocalDate end, ContractStatus initial) {
        Contract c = new Contract();
        c.setClientId(1L);
        c.setStartDate(end.minusYears(1));
        c.setEndDate(end);
        c.setStatus(initial);
        return contracts.save(c);
    }

    @Test
    void pastEndDate_becomesEnded() {
        Long id = contractEndingOn(LocalDate.now().minusDays(1), ContractStatus.ACTIVE).getId();
        service.applyTransitions();
        assertEquals(ContractStatus.ENDED, contracts.findById(id).orElseThrow().getStatus());
    }

    @Test
    void within30Days_becomesExpiring() {
        Long id = contractEndingOn(LocalDate.now().plusDays(10), ContractStatus.ACTIVE).getId();
        service.applyTransitions();
        assertEquals(ContractStatus.EXPIRING, contracts.findById(id).orElseThrow().getStatus());
    }

    @Test
    void farFuture_staysActive() {
        Long id = contractEndingOn(LocalDate.now().plusDays(90), ContractStatus.EXPIRING).getId();
        service.applyTransitions();
        assertEquals(ContractStatus.ACTIVE, contracts.findById(id).orElseThrow().getStatus());
    }

    @Test
    void pureFunction_statusFor() {
        LocalDate today = LocalDate.of(2026, 8, 31);
        assertEquals(ContractStatus.ENDED, ContractStatusService.statusFor(today, today.minusDays(1)));
        assertEquals(ContractStatus.EXPIRING, ContractStatusService.statusFor(today, today.plusDays(5)));
        assertEquals(ContractStatus.ACTIVE, ContractStatusService.statusFor(today, today.plusDays(60)));
    }
}
