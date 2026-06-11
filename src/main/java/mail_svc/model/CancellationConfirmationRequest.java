package mail_svc.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CancellationConfirmationRequest {

    private String recipientEmail;

    private String userFirstName;

    private String userLastName;

    private String agreementTitle;

    private LocalDateTime cancelledAt;

    private String cancelInitiatedBy;

    private UUID agreementId;
}
