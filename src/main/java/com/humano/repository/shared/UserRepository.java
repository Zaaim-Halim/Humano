package com.humano.repository.shared;

import com.humano.domain.shared.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link User} entity.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findOneByActivationKey(String activationKey);

    @Query("SELECT u FROM User u WHERE u.activated = false AND u.activationKey IS NOT NULL AND u.createdDate < :dateTime")
    List<User> findAllByActivatedIsFalseAndActivationKeyIsNotNullAndCreatedDateBefore(@Param("dateTime") Instant dateTime);

    Optional<User> findOneByResetKey(String resetKey);

    Optional<User> findOneByEmailIgnoreCase(String email);

    Optional<User> findOneByLogin(String login);

    @EntityGraph(attributePaths = "authorities")
    Optional<User> findOneWithAuthoritiesByLogin(String login);

    @EntityGraph(attributePaths = "authorities")
    Optional<User> findOneWithAuthoritiesByEmailIgnoreCase(String email);

    Page<User> findAllByIdNotNullAndActivatedIsTrue(Pageable pageable);

    /**
     * Users matching a search term (login, email or full name; {@code term} is a lower-cased
     * {@code %like%} pattern, or null for all) and optionally holding a role — server-side paging
     * for the user administration screen.
     */
    @Query(
        value = "SELECT u FROM User u WHERE " +
        "(:term IS NULL OR LOWER(u.login) LIKE :term OR LOWER(u.email) LIKE :term " +
        " OR LOWER(CONCAT(COALESCE(u.firstName, ''), ' ', COALESCE(u.lastName, ''))) LIKE :term) " +
        "AND (:role IS NULL OR EXISTS (SELECT 1 FROM User u2 JOIN u2.authorities a WHERE u2 = u AND a.name = :role))",
        countQuery = "SELECT COUNT(u) FROM User u WHERE " +
        "(:term IS NULL OR LOWER(u.login) LIKE :term OR LOWER(u.email) LIKE :term " +
        " OR LOWER(CONCAT(COALESCE(u.firstName, ''), ' ', COALESCE(u.lastName, ''))) LIKE :term) " +
        "AND (:role IS NULL OR EXISTS (SELECT 1 FROM User u2 JOIN u2.authorities a WHERE u2 = u AND a.name = :role))"
    )
    Page<User> search(@Param("term") String term, @Param("role") String role, Pageable pageable);

    /** How many users hold each role: rows of {@code [roleName, count]}. */
    @Query("SELECT a.name, COUNT(u) FROM User u JOIN u.authorities a GROUP BY a.name")
    List<Object[]> countByAuthority();

    /**
     * P4.3 — Lookup the first activated user holding the named authority, ordered
     * by creation date. Used to resolve the tenant's primary contact email for
     * billing notifications, where the principal is "whoever was seeded as the
     * admin during onboarding" (P1.5). Returns {@code Optional.empty()} for
     * tenants where no admin row exists (provisioning crashed mid-flow, etc.).
     */
    @Query("SELECT u FROM User u JOIN u.authorities a WHERE a.name = :authority " + "AND u.activated = true ORDER BY u.createdDate ASC")
    List<User> findActivatedByAuthority(@Param("authority") String authority);
}
