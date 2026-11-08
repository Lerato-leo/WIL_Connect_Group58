package wil_connect.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import wil_connect.entity.DutyLog;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DutyLogRepository extends JpaRepository<DutyLog, Long> {

    Optional<DutyLog> findByPlacementPlacementIdAndLogDate(Long placementId, LocalDate logDate);

    List<DutyLog> findByPlacementPlacementIdOrderByLogDateDesc(Long placementId);

    @Query("""
           SELECT d FROM DutyLog d
           WHERE d.placement.supervisor.supervisorId = :supervisorId
             AND d.status = 'PENDING'
           ORDER BY d.createdAt ASC
           """)
    List<DutyLog> findPendingForSupervisor(@Param("supervisorId") Long supervisorId);

    @Query("""
           SELECT d FROM DutyLog d
           WHERE d.placement.student.studentId = :studentId
           ORDER BY d.logDate DESC
           """)
    List<DutyLog> findByStudentId(@Param("studentId") Long studentId);
}
