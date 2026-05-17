package kr.devslab.apilog.event;

import kr.devslab.apilog.model.dto.ApiRequest;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

@Getter
public class ApiCallInitiatedEvent extends ApplicationEvent {
    private final ApiRequest request;
    private final LocalDateTime eventTimestamp;

    public ApiCallInitiatedEvent(Object source, ApiRequest request) {
        super(source);
        this.request = request;
        this.eventTimestamp = LocalDateTime.now();
    }

}