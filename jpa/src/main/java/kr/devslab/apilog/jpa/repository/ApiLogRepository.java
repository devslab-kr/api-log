package kr.devslab.apilog.jpa.repository;

import kr.devslab.apilog.jpa.model.ApiLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link ApiLogEntity}. Only convenience
 * lookups are exposed; rich querying belongs in the consumer's own services
 * (this starter's job is to keep the table populated, not to be a reporting API).
 */
@Repository
public interface ApiLogRepository extends JpaRepository<ApiLogEntity, Long> {

    List<ApiLogEntity> findByRequestId(String requestId);

    List<ApiLogEntity> findByEventType(String eventType);

    List<ApiLogEntity> findByEndpoint(String endpoint);
}
