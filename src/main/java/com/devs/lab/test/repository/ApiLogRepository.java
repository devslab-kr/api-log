package com.devs.lab.test.repository;

import com.devs.lab.test.model.ApiLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApiLogRepository extends JpaRepository<ApiLogEntity, Long> {

    List<ApiLogEntity> findByRequestId(String requestId);

    List<ApiLogEntity> findByEventType(String eventType);

    List<ApiLogEntity> findByEndpoint(String endpoint);
}