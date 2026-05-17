package kr.devslab.apilog.repository;

import kr.devslab.apilog.model.ApiLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApiLogRepository extends JpaRepository<ApiLogEntity, Long> {

    List<ApiLogEntity> findByRequestId(String requestId);

    List<ApiLogEntity> findByEventType(String eventType);

    List<ApiLogEntity> findByEndpoint(String endpoint);
}