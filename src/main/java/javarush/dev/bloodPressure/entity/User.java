package javarush.dev.bloodPressure.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    boolean isDoctor;
    private String firstName;
    private String lastName;
    private String username;
    private String password;
    private String email;
    private String confirmEmail;
    private String role;
    private Integer age;
    private Long chatId;
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private List<BloodPressureMeasurement> bloodPressureMeasurements;
    private String doctor; // Доктор, который обследует
    private String medication;
    @OneToMany
    List<User> patients;
}


