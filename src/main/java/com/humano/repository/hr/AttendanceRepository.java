package com.humano.repository.hr;

import com.humano.domain.hr.Attendance;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link Attendance} entity.
 */
@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, UUID>, JpaSpecificationExecutor<Attendance> {
    Page<Attendance> findByEmployeeId(UUID employeeId, Pageable pageable);

    Optional<Attendance> findFirstByEmployeeIdAndDate(UUID employeeId, LocalDate date);

    @EntityGraph(attributePaths = "events")
    List<Attendance> findByEmployeeIdAndDateBetweenOrderByDate(UUID employeeId, LocalDate startDate, LocalDate endDate);

    Page<Attendance> findByEmployeeIdAndDateBetween(UUID employeeId, LocalDate startDate, LocalDate endDate, Pageable pageable);
}
