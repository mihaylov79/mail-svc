package mail_svc.web.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;
@Builder
@Data
public class NotificationRequest {

    @NotNull
    private UUID recipientId;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotBlank
    private String title;

    @NotBlank
    private String content;
}
