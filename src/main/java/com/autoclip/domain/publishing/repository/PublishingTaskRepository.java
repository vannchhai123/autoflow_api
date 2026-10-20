package com.autoclip.domain.publishing.repository;

import com.autoclip.domain.publishing.entity.PublishingStatus;
import com.autoclip.domain.publishing.entity.PublishingTask;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublishingTaskRepository extends JpaRepository<PublishingTask, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select t from PublishingTask t
            where t.status = :status and t.scheduledTime <= :now
            order by t.scheduledTime asc
            """)
    List<PublishingTask> findDueForUpdate(@Param("status") PublishingStatus status,
                                          @Param("now") Instant now,
                                          Pageable pageable);

    @EntityGraph(attributePaths = {"videoProject", "socialAccount"})
    Optional<PublishingTask> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"videoProject", "socialAccount"})
    List<PublishingTask> findTop50ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"videoProject", "socialAccount"})
    @Query("""
            select t from PublishingTask t
            where t.videoProject.owner.id = :ownerId or t.socialAccount.owner.id = :ownerId
            order by t.scheduledTime desc, t.createdAt desc
            """)
    List<PublishingTask> findByOwnerId(@Param("ownerId") UUID ownerId);

    long countByStatus(PublishingStatus status);
}
