package com.smartdesk.repo;

import com.smartdesk.domain.Contract;
import com.smartdesk.domain.Enums;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractRepo extends JpaRepository<Contract, Long> {
    List<Contract> findByClientId(Long clientId);
    List<Contract> findByClientIdAndStatusNot(Long clientId, Enums.ContractStatus status);
    List<Contract> findByStatusNot(Enums.ContractStatus status);
}
