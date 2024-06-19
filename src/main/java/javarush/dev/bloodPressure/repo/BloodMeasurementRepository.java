package javarush.dev.bloodPressure.repo;

import javarush.dev.bloodPressure.entity.BloodPressureMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BloodMeasurementRepository extends JpaRepository<BloodPressureMeasurement,Long> {
}
