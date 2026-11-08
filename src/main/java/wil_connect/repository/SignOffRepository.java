package wil_connect.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import wil_connect.entity.SignOff;

import java.util.List;

public interface SignOffRepository extends JpaRepository<SignOff, Long> {

    List<SignOff> findByDutyLogLogIdOrderBySignoffDateDesc(Long logId);
}
