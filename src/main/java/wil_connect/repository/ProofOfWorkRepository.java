package wil_connect.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import wil_connect.entity.ProofOfWork;

import java.util.List;

public interface ProofOfWorkRepository extends JpaRepository<ProofOfWork, Long> {

    List<ProofOfWork> findByDutyLogLogId(Long logId);
}
