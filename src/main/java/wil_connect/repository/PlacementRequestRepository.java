package wil_connect.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import wil_connect.entity.PlacementRequest;

import java.util.List;

@Repository
public interface PlacementRequestRepository extends JpaRepository<PlacementRequest, Integer> {

    List<PlacementRequest> findByStudent_StudentId(Integer studentId);

    List<PlacementRequest> findByEmployer_EmployerId(Integer employerId);

    List<PlacementRequest> findByStudent_UserAccount_UserId(Long userId);

    List<PlacementRequest> findByEmployer_UserAccount_UserId(Long userId);

    boolean existsByStudent_StudentIdAndEmployer_EmployerIdAndStatusIn(
            Integer studentId,
            Integer employerId,
            List<String> statuses);
}