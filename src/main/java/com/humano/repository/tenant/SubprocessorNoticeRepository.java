package com.humano.repository.tenant;

import com.humano.domain.tenant.SubprocessorNotice;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubprocessorNoticeRepository extends JpaRepository<SubprocessorNotice, UUID> {
    List<SubprocessorNotice> findAllByOrderBySentAtDesc();
}
