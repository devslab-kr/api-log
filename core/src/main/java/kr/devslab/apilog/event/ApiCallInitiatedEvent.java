package kr.devslab.apilog.event;

import kr.devslab.apilog.dto.ApiRequest;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * Fired just before an outbound HTTP call leaves the client. The listener
 * persists an {@code INITIATED} row so the call is traceable even if the
 * response never arrives.
 */
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
