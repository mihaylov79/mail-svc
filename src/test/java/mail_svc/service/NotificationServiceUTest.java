package mail_svc.service;


import mail_svc.model.NotificationPreference;
import mail_svc.repository.NotificationPreferenceRepository;
import mail_svc.repository.NotificationRepository;
import mail_svc.web.dto.NotificationPreferenceRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    @Test
    void given_ExistingNotificationPreference_when_changeNotificationPreference_then_NotificationIsChanged(){

        UUID recipientId = UUID.randomUUID();
        boolean enabled = true;

        NotificationPreference preference = NotificationPreference.builder().enabled(false).build();
        when(notificationPreferenceRepository.findByRecipientId(recipientId)).thenReturn(Optional.of(preference));

        notificationService.changeNotificationPreference(recipientId,true);

        verify(notificationPreferenceRepository,times(1))
                        .save(Mockito.argThat(NotificationPreference::isEnabled));


    }

    @Test
    void given_ExistingNotificationPreference_whenUpdateNotificationPreference_then_updatesSuccessfully(){

        UUID recipientId = UUID.randomUUID();
        NotificationPreferenceRequest preferenceRequest = new NotificationPreferenceRequest();
        preferenceRequest.setRecipientId(recipientId);
        preferenceRequest.setInfo("test@example.com");
        preferenceRequest.setEnabled(true);

        NotificationPreference preference = NotificationPreference.builder()
                .recipientId(recipientId)
                .info("old@example.com")
                .enabled(false).build();


        // Настройваме mock за repository
        when(notificationPreferenceRepository.findByRecipientId(recipientId))
                .thenReturn(Optional.of(preference));

        when(notificationPreferenceRepository.save(any(NotificationPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Извикваме метода за актуализация
        NotificationPreference updatedPreference = notificationService.updateNotificationPreference(preferenceRequest);

        // Проверки
        assertNotNull(updatedPreference);
        assertEquals(preferenceRequest.getInfo(), updatedPreference.getInfo());
        assertTrue(updatedPreference.isEnabled());
        verify(notificationPreferenceRepository, times(1)).save(updatedPreference);
    }




}
