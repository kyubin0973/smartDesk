package com.smartdesk.feature.contract;

import com.smartdesk.domain.Contract;
import com.smartdesk.domain.Enums.ContractStatus;
import com.smartdesk.repo.ContractRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 계약 상태(ACTIVE → EXPIRING → ENDED) 자동 전이 로직.
 * 스케줄/부팅 트리거는 ContractStatusJob (test 프로파일에서 비활성).
 */
@Service
public class ContractStatusService {

    private static final Logger log = LoggerFactory.getLogger(ContractStatusService.class);
    static final int EXPIRING_WINDOW_DAYS = 30;

    private final ContractRepo contracts;

    public ContractStatusService(ContractRepo contracts) {
        this.contracts = contracts;
    }

    public static ContractStatus statusFor(LocalDate today, LocalDate endDate) {
        if (today.isAfter(endDate)) return ContractStatus.ENDED;
        if (!today.isBefore(endDate.minusDays(EXPIRING_WINDOW_DAYS))) return ContractStatus.EXPIRING;
        return ContractStatus.ACTIVE;
    }

    /** @return 상태가 바뀐 계약 수 */
    @Transactional
    public int applyTransitions() {
        LocalDate today = LocalDate.now();
        List<Contract> live = contracts.findByStatusNot(ContractStatus.ENDED);
        int changed = 0;
        for (Contract c : live) {
            ContractStatus next = statusFor(today, c.getEndDate());
            if (next != c.getStatus()) {
                c.setStatus(next);
                contracts.save(c);
                changed++;
            }
        }
        if (changed > 0) log.info("[contract-status] {}건 전이", changed);
        return changed;
    }
}
