package wil_connect.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "EMPLOYER")
@Getter
@Setter
@NoArgsConstructor
public class Employer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employer_id")
    private Integer employerId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserAccount userAccount;

    @Column(name = "employer_name", nullable = false)
    private String employerName;

    @Column(name = "address")
    private String address;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_number")
    private String contactNumber;

    @Column(name = "status", nullable = false)
    private String status;
}