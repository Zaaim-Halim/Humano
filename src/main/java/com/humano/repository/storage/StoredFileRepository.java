package com.humano.repository.storage;

import com.humano.domain.storage.StoredFile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link StoredFile} metadata entries.
 */
@Repository
public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {
    Optional<StoredFile> findByPublicToken(String publicToken);

    Page<StoredFile> findByOwnerTypeAndOwnerIdAndDeletedAtIsNull(String ownerType, UUID ownerId, Pageable pageable);

    @Query(
        "SELECT sf FROM StoredFile sf WHERE sf.ownerType = :ownerType AND sf.ownerId = :ownerId AND sf.context = :context AND sf.deletedAt IS NULL"
    )
    Page<StoredFile> findByOwnerAndContext(
        @Param("ownerType") String ownerType,
        @Param("ownerId") UUID ownerId,
        @Param("context") com.humano.domain.enumeration.storage.FileContext context,
        Pageable pageable
    );

    @Query(
        "SELECT sf FROM StoredFile sf WHERE sf.context = :context AND sf.visibility = com.humano.domain.enumeration.storage.FileVisibility.PUBLIC AND sf.deletedAt IS NULL"
    )
    Page<StoredFile> findPublicByContext(@Param("context") com.humano.domain.enumeration.storage.FileContext context, Pageable pageable);
}
