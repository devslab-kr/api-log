package kr.devslab.apilog.service;

import kr.devslab.apilog.event.ApiCallErrorEvent;
import kr.devslab.apilog.event.ApiCallInitiatedEvent;
import kr.devslab.apilog.event.ApiCallSuccessEvent;
import kr.devslab.apilog.model.ApiLogEntity;
import kr.devslab.apilog.repository.ApiLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static kr.devslab.apilog.Constants.ERROR;
import static kr.devslab.apilog.Constants.INITIATED;
import static kr.devslab.apilog.Constants.RETRY_ERROR;
import static kr.devslab.apilog.Constants.SUCCESS;

@Service
@RequiredArgsConstructor
public class ApiLogService {

    private final ApiLogRepository repository;
    private final ObjectMapper objectMapper;

    public void saveApiCallInitiated(ApiCallInitiatedEvent event) {
        ApiLogEntity entity = ApiLogEntity.builder()
                .eventType(INITIATED)
                .requestId(event.getRequest().getRequestId())
                .endpoint(event.getRequest().getEndpoint())
                .payload(toJsonNode(event.getRequest().getPayload()))
                .timestamp(LocalDateTime.now())
                .retryCount(0)
                .isRetry(false)
                .build();
        repository.save(entity);
    }

    public void saveApiCallSuccess(ApiCallSuccessEvent event) {
        ApiLogEntity entity = ApiLogEntity.builder()
                .eventType(SUCCESS)
                .requestId(event.getRequest().getRequestId())
                .endpoint(event.getRequest().getEndpoint())
                .payload(toJsonNode(event.getRequest().getPayload()))
                .response(toJsonNode(event.getResponse().getData()))
                .statusCode(event.getResponse().getStatusCode())
                .timestamp(LocalDateTime.now())
                .retryCount(0)
                .isRetry(false)
                .build();
        repository.save(entity);
    }

    public void saveApiCallError(ApiCallErrorEvent event) {
        ApiLogEntity entity = ApiLogEntity.builder()
                .eventType(event.isRetry() ? RETRY_ERROR : ERROR)
                .requestId(event.getRequest().getRequestId())
                .endpoint(event.getRequest().getEndpoint())
                .payload(toJsonNode(event.getRequest().getPayload()))
                .errorMessage(toJsonNode(event.getError().getMessage()))
                .timestamp(LocalDateTime.now())
                .retryCount(event.getRetryCount())
                .isRetry(event.isRetry())
                .build();
        repository.save(entity);
    }

    private JsonNode toJsonNode(String data) {
        if (data == null) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(data);
        } catch (Exception e) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("raw", data);
            return node;
        }
    }
}