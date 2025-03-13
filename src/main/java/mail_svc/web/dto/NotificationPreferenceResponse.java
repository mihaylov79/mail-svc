package mail_svc.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;
@Data
@Builder
public class NotificationPreferenceResponse {

    private UUID id;

    private UUID recipientId;


    private boolean enabled;


    private String info;
}
