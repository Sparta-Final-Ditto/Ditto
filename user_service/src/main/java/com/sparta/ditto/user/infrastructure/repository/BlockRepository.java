package com.sparta.ditto.user.infrastructure.repository;

import com.sparta.ditto.user.domain.block.Block;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockRepository extends JpaRepository<Block, UUID> {

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    Optional<Block> findByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

}
