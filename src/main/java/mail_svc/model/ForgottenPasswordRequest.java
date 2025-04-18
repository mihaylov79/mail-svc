package mail_svc.model;


import lombok.Data;

@Data
public class ForgottenPasswordRequest {

    private String recipient;

    private String title;

    private String content;
}
