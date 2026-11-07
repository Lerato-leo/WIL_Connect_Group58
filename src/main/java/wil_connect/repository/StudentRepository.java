package wil_connect.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import wil_connect.entity.Student;

import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Integer> {

    Optional<Student> findByUserAccount_UserId(Long userId);

    Optional<Student> findByStudentNumber(String studentNumber);
}