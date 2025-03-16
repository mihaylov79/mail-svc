package mail_svc.web.dto;

import lombok.Builder;
import lombok.Data;
import mail_svc.model.NotificationStatus;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponse {

    private String title;

    private String content;

    private NotificationStatus status;

    private LocalDateTime created;
}
