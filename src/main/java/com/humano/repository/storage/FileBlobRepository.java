package com.humano.repository.storage;

import com.humano.domain.storage.FileBlob;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link FileBlob} — the raw bytes table.
 * Only exists to support the DATABASE storage backend.
 */
@Repository
public interface FileBlobRepository extends JpaRepository<FileBlob, UUID> {}
