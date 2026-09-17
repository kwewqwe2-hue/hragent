package com.hragent.hragentv1.repo;
import com.hragent.hragentv1.domain.LeaveMedicalRecord;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface LeaveMedicalRecordRepository extends JpaRepository<LeaveMedicalRecord,Long> {
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select m from LeaveMedicalRecord m where m.id = :id")
 Optional<LeaveMedicalRecord> lockById(Long id);
}
