package javarush.dev.bloodPressure.repo;

import javarush.dev.bloodPressure.entity.BloodPressureMeasurement;
import javarush.dev.bloodPressure.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BloodMeasurementRepository extends JpaRepository<BloodPressureMeasurement,Long> {
}
