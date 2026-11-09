package wil_connect.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import wil_connect.entity.Placement;

import java.util.List;

@Repository
public interface PlacementRepository extends JpaRepository<Placement, Integer> {

    boolean existsByStudent_StudentIdAndEmployer_EmployerIdAndStatus(
            Integer studentId,
            Integer employerId,
            String status);

    List<Placement> findByStudent_StudentId(Integer studentId);

    List<Placement> findByEmployer_EmployerId(Integer employerId);
}