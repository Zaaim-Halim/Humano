package com.humano.repository.hr;

import com.humano.domain.enumeration.hr.CompensationProposalStatus;
import com.humano.domain.hr.CompensationProposal;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link CompensationProposal} entity.
 */
@Repository
public interface CompensationProposalRepository extends JpaRepository<CompensationProposal, UUID> {
    /** A cycle's proposals with what the page shows. */
    @EntityGraph(attributePaths = { "employee", "employee.position", "review", "currency", "currentPosition", "newPosition" })
    List<CompensationProposal> findByCycleId(UUID cycleId);

    /** One proposal with what the page shows. */
    @EntityGraph(attributePaths = { "cycle", "employee", "employee.position", "review", "currency", "currentPosition", "newPosition" })
    Optional<CompensationProposal> findWithDetailsById(UUID id);

    /** One proposal, locked until the transaction ends — so it is applied once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CompensationProposal p where p.id = :id")
    Optional<CompensationProposal> findForUpdate(@Param("id") UUID id);

    /** The reviews of a cycle that already have a proposal. */
    @Query("select p.review.id from CompensationProposal p where p.cycle.id = :cycleId")
    List<UUID> findReviewIdsByCycleId(@Param("cycleId") UUID cycleId);

    long countByCycleId(UUID cycleId);

    /** Proposals of a cycle not yet applied (draft, submitted or approved). */
    long countByCycleIdAndStatusNot(UUID cycleId, CompensationProposalStatus status);
}
