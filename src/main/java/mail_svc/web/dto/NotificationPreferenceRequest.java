package mail_svc.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class NotificationPreferenceRequest {

@NotNull
private UUID recipientId;


private boolean enabled;

@NotBlank
private String info;
}
