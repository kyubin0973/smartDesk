package com.smartdesk.repo;

import com.smartdesk.domain.SystemAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemAssetRepo extends JpaRepository<SystemAsset, Long> {
    List<SystemAsset> findByClientIdAndActiveTrue(Long clientId);
}
