package wil_connect.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import wil_connect.entity.Supervisor;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupervisorRepository extends JpaRepository<Supervisor, Integer> {

    Optional<Supervisor> findByUserAccount_UserId(Long userId);

    List<Supervisor> findByEmployer_EmployerId(Integer employerId);
}