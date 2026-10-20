package com.autoclip.domain.asset.repository;

import com.autoclip.domain.asset.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {
}
