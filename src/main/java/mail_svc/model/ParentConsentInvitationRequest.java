package mail_svc.model;


import lombok.Data;

@Data
public class ParentConsentInvitationRequest {

    private String parentEmail;

    private String childFirstName;

    private String childLastName;

    private String agreementTitle;

    private String consentLink;

}
