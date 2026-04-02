package mail_svc.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ParentConsentConfirmationRequest {


    private String parentEmail;

    private String childFirstName;

    private String childLastName;

    private String agreementTitle;

    private String agreementContent;

    private LocalDateTime parentConsentAt;

    private UUID consentId;


}
