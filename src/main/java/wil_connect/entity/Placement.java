package wil_connect.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "PLACEMENT")
@Getter
@Setter
@NoArgsConstructor
public class Placement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "placement_id")
    private Integer placementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employer_id", nullable = false)
    private Employer employer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_id", nullable = false)
    private Supervisor supervisor;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status;
}