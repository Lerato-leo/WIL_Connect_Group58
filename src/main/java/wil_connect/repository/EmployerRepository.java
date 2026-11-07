package wil_connect.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import wil_connect.entity.Employer;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployerRepository extends JpaRepository<Employer, Integer> {

    Optional<Employer> findByUserAccount_UserId(Long userId);

    @Query(value = """
        SELECT *
        FROM EMPLOYER
        WHERE LOWER(status) = LOWER('approved')
        """, nativeQuery = true)
    List<Employer> findByStatusIgnoreCase (@Param("status") String status);


    @Query(value = """
        SELECT *
        FROM EMPLOYER
        WHERE LOWER(employer_name) LIKE LOWER(CONCAT('%', :employerName, '%'))
        AND LOWER(status) = LOWER(:status)
        """, nativeQuery = true)
    List<Employer> findByEmployerNameContainingIgnoreCaseAndStatusIgnoreCase(
            @Param("employerName") String employerName,
            @Param("status") String status);
}