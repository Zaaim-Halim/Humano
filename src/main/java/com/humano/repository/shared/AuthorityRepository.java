package com.humano.repository.shared;

import com.humano.domain.shared.Authority;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Authority entity.
 */
@SuppressWarnings("unused")
@Repository
public interface AuthorityRepository extends JpaRepository<Authority, String> {
    /**
     * Load every authority with its permissions eagerly in a single query. Used by
     * {@code AuthorityPermissionService} to build the per-tenant permission cache without
     * relying on an open session for lazy traversal (the cache is populated lazily from a
     * {@code computeIfAbsent} lambda, where self-invocation would otherwise bypass the
     * transactional proxy).
     */
    @Query("select distinct a from Authority a left join fetch a.permissions")
    List<Authority> findAllWithPermissions();
}
