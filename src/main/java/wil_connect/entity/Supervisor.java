package wil_connect.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "SUPERVISOR")
@Getter
@Setter
@NoArgsConstructor
public class Supervisor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supervisor_id")
    private Integer supervisorId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserAccount userAccount;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "verification_pin_hash", nullable = false)
    private String verificationPinHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employer_id", nullable = false)
    private Employer employer;
}