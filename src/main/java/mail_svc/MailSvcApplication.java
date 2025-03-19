package mail_svc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MailSvcApplication {

	public static void main(String[] args) {
		SpringApplication.run(MailSvcApplication.class, args);
	}

}
