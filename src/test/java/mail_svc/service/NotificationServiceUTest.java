package mail_svc.service;


import mail_svc.repository.NotificationPreferenceRepository;
import mail_svc.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceUTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationPreferenceRepository notificationPreferenceRepository;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private SpringTemplateEngine templateEngine;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void given_NonExistingRecipientId_when_changeNotificationPreference(){

        UUID recipientId = UUID.randomUUID();
        boolean enabled = true;
        when(notificationPreferenceRepository.findByRecipientId(recipientId)).thenReturn(Optional.empty());

        assertThrows(NullPointerException.class,()-> notificationService.changeNotificationPreference(recipientId,enabled));

    }


}
