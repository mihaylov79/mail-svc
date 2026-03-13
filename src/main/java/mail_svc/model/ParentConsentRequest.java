package mail_svc.model;


import lombok.Data;

@Data
public class ParentConsentRequest {

    private String parentEmail;

    private String childFirstName;

    private String childLastName;

    private String agreementContent;

    private String consentLink;

}
