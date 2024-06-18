package javarush.dev.bloodPressure.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class BloodPressureMeasurement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int systolic; // Систолическое давление
    private int diastolic; // Диастолическое давление
    private LocalDateTime measurementTime; // Дата и время измерения

}
