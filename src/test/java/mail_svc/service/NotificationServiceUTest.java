package mail_svc.service;


import jakarta.mail.internet.MimeMessage;
import mail_svc.model.Notification;
import mail_svc.model.NotificationPreference;
import mail_svc.model.NotificationStatus;
import mail_svc.repository.NotificationPreferenceRepository;
import mail_svc.repository.NotificationRepository;
import mail_svc.web.dto.NotificationPreferenceRequest;
import mail_svc.web.dto.NotificationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.LocalDateTime;
import java.util.List;
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



        when(notificationPreferenceRepository.findByRecipientId(recipientId))
                .thenReturn(Optional.of(preference));

        when(notificationPreferenceRepository.save(any(NotificationPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationPreference updatedPreference = notificationService.updateNotificationPreference(preferenceRequest);


        assertNotNull(updatedPreference);
        assertEquals(preferenceRequest.getInfo(), updatedPreference.getInfo());
        assertTrue(updatedPreference.isEnabled());
        verify(notificationPreferenceRepository, times(1)).save(updatedPreference);
    }

    @Test
    void given_NonExistingNotificationPreference_whenUpdateNotificationPreference_then_createNew(){

        UUID recipientId = UUID.randomUUID();
        NotificationPreferenceRequest preferenceRequest = new NotificationPreferenceRequest();
        preferenceRequest.setRecipientId(recipientId);
        preferenceRequest.setInfo("test@example.com");
        preferenceRequest.setEnabled(true);


        when(notificationPreferenceRepository.findByRecipientId(recipientId))
                .thenReturn(Optional.empty());

        //мокваме - save - метода за да връща обекта който му е подаден. за да избегнем изпълнението му върху базата
        when(notificationPreferenceRepository.save(any(NotificationPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));


        NotificationPreference newPreference = notificationService.updateNotificationPreference(preferenceRequest);


        assertNotNull(newPreference);
        assertEquals(preferenceRequest.getInfo(), newPreference.getInfo());
        assertTrue(newPreference.isEnabled());
        verify(notificationPreferenceRepository, times(1)).save(newPreference);
    }

    @Test
    void given_NotificationRequest_when_sendMailNotification_shouldSendNotificationSuccessfully() {


        NotificationPreference notificationPreference = NotificationPreference.builder()
                .recipientId(UUID.randomUUID())
                .info("old@example.com")
                .enabled(true).build();


        NotificationRequest notificationRequest = NotificationRequest.builder()
                .recipientId(notificationPreference.getRecipientId())
                .firstName("John")
                .lastName("Doe")
                .content("This is a test notification.")
                .title("Test Notification")
                .build();


        when(notificationPreferenceRepository.findByRecipientId(any(UUID.class)))
                .thenReturn(java.util.Optional.of(notificationPreference));

        MimeMessage mimeMessage = mock(MimeMessage.class);
        MimeMessageHelper helper = mock(MimeMessageHelper.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        when(templateEngine.process(any(String.class), any())).thenReturn("<html><body>Test content</body></html>");

        //мокваме - save - метода за да връща обекта който му е подаден. за да избегнем изпълнението му върху базата
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Notification result = notificationService.sendMailNotification(notificationRequest);

        verify(mailSender).send(mimeMessage);
        verify(notificationRepository).save(any(Notification.class));

        assert(result.getStatus() == NotificationStatus.COMPLETED);
    }


    @Test
    void given_NotificationRequest_when_sendMailNotificationIfExceptionIsThrown_shouldFailToSendNotification() {
        // Arrange
        UUID recipientId = UUID.randomUUID();

        NotificationPreference notificationPreference = NotificationPreference.builder()
                .recipientId(recipientId)
                .info("old@example.com")
                .enabled(true).build();

        NotificationRequest notificationRequest = NotificationRequest.builder()
                .recipientId(recipientId)
                .firstName("John")
                .lastName("Doe")
                .content("This is a test notification.")
                .title("Test Notification")
                .build();

        when(notificationPreferenceRepository.findByRecipientId(recipientId))
                .thenReturn(Optional.of(notificationPreference));  // Добавено мокване

        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("Mail sending failed"));

        // Act
        notificationService.sendMailNotification(notificationRequest);

        // Assert
        verify(notificationRepository).save(argThat(n -> n.getStatus() == NotificationStatus.FAILED));
    }

    @Test
    void given_NotificationPreferenceIsDisabled_when_sendMailNotification_shouldThrowException() {

        NotificationPreference notificationPreference = NotificationPreference.builder()
                .recipientId(UUID.randomUUID())
                .info("old@example.com")
                .enabled(false).build();


        NotificationRequest notificationRequest = NotificationRequest.builder()
                .recipientId(notificationPreference.getRecipientId())
                .firstName("John")
                .lastName("Doe")
                .content("This is a test notification.")
                .title("Test Notification")
                .build();


        when(notificationPreferenceRepository.findByRecipientId(any(UUID.class)))
                .thenReturn(java.util.Optional.of(notificationPreference));


        assertThrows(IllegalArgumentException.class, () -> notificationService.sendMailNotification(notificationRequest));
    }

    @Test
    void givenHistory_when_clearNotificationHistory_thenShouldSetDeleteFieldTrue() {
        // Arrange
        UUID recipientId = UUID.randomUUID();
        List<Notification> notifications = List.of(
                Notification.builder().id(UUID.randomUUID()).recipientId(recipientId).deleted(false).build(),
                Notification.builder().id(UUID.randomUUID()).recipientId(recipientId).deleted(false).build()
        );

        when(notificationRepository.findAllByRecipientIdAndDeleted(recipientId, false)).thenReturn(notifications);

        // Act
        notificationService.clearNotificationHistory(recipientId);

        // Assert
        for (Notification notification : notifications) {
            verify(notificationRepository).save(argThat(n -> n.getId().equals(notification.getId()) && n.isDeleted()));
        }
    }

    @Test
    void given_schedulerEveryMonth_when_CleanUpOldNotifications_the_deletedShouldBeSetToTrue() {



        when(notificationRepository.deleteByDeletedTrueAndCreatedBefore(any(LocalDateTime.class))).thenReturn(5);

        notificationService.cleanUpOldNotifications();

        verify(notificationRepository).deleteByDeletedTrueAndCreatedBefore(any(LocalDateTime.class));
    }


}
