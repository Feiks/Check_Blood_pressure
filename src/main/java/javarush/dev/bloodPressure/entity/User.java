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
    private String username;
    private String password;
    private String role;
    private Long chatId;
    @OneToMany
    private List<BloodPressureMeasurement> bloodPressureMeasurements;
    private String doctor; // Доктор, который обследует
    private String medication;
    @OneToMany
    List<User> patients;
}


