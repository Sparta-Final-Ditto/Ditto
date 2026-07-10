package com.sparta.ditto.user.infrastructure.repository;

import com.sparta.ditto.user.domain.block.Block;
import com.sparta.ditto.user.domain.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockRepository extends JpaRepository<Block, UUID> {

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    Optional<Block> findByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    @Query("SELECT b.blocked FROM Block b WHERE b.blocker.id = :blockerId")
    List<User> findBlockedUsersByBlockerId(@Param("blockerId") UUID blockerId);

    @Query("SELECT b.blocked.id FROM Block b WHERE b.blocker.id = :userId")
    List<UUID> findBlockedUserIdsByBlockerId(@Param("userId") UUID userId);

    @Query("SELECT b.blocker.id FROM Block b WHERE b.blocked.id = :userId")
    List<UUID> findBlockerUserIdsByBlockedId(@Param("userId") UUID userId);
}
