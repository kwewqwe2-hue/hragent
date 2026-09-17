package com.hragent.hragentv1.repo;
import com.hragent.hragentv1.domain.CertificateSignJob;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface CertificateSignJobRepository extends JpaRepository<CertificateSignJob,Long> {
 List<CertificateSignJob> findTop10ByStatusInOrderByUpdatedAtAsc(Collection<String> statuses);
 @Modifying @Query("update CertificateSignJob j set j.status=:next, j.updatedAt=CURRENT_TIMESTAMP, j.version=j.version+1 where j.certificateId=:id and j.status=:expected and j.version=:version")
 int claim(@Param("id") Long id,@Param("expected") String expected,@Param("next") String next,@Param("version") Long version);
}
