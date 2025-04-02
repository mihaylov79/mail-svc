package mail_svc;


import mail_svc.model.NotificationPreference;
import mail_svc.repository.NotificationPreferenceRepository;
import mail_svc.service.NotificationService;
import mail_svc.web.dto.NotificationPreferenceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@SpringBootTest
public class CreateNewPreferenceITest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Test
    void createNewNotificationPreference(){

        UUID recipientId = UUID.randomUUID();
        NotificationPreferenceRequest preferenceDTO = new NotificationPreferenceRequest();
        preferenceDTO.setRecipientId(recipientId);
        preferenceDTO.setEnabled(true);
        preferenceDTO.setInfo("test@example.com");

        notificationService.updateNotificationPreference(preferenceDTO);

        List<NotificationPreference> preferences = preferenceRepository.findAll();
        assertEquals(1,preferences.size());
        NotificationPreference preference = preferences.get(0);
        assertEquals(recipientId,preference.getRecipientId());


    }

}
