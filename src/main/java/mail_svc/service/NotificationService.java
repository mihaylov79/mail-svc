package mail_svc.service;

import jakarta.mail.internet.MimeMessage;
import mail_svc.model.Notification;
import mail_svc.model.NotificationPreference;
import mail_svc.model.NotificationStatus;
import mail_svc.repository.NotificationPreferenceRepository;
import mail_svc.repository.NotificationRepository;
import mail_svc.web.dto.NotificationPreferenceRequest;
import mail_svc.web.dto.NotificationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final JavaMailSender mailSender;

    @Autowired
    public NotificationService(NotificationRepository notificationRepository, NotificationPreferenceRepository notificationPreferenceRepository, JavaMailSender mailSender) {
        this.notificationRepository = notificationRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.mailSender = mailSender;
    }

    public NotificationPreference updateNotificationPreference(NotificationPreferenceRequest preferenceRequest) {

        NotificationPreference preference = notificationPreferenceRepository.findByRecipientId(preferenceRequest.getRecipientId())
                .map(p -> p.toBuilder()
                        .info(preferenceRequest.getInfo())
                        .enabled(preferenceRequest.isEnabled())
                        .updated(LocalDateTime.now())
                        .build()).orElse(NotificationPreference.builder()
                        .recipientId(preferenceRequest.getRecipientId())
                        .info(preferenceRequest.getInfo())
                        .enabled(preferenceRequest.isEnabled())
                        .created(LocalDateTime.now())
                        .updated(LocalDateTime.now())
                        .build());

        return notificationPreferenceRepository.save(preference);
    }

    public NotificationPreference getPreferenceByRecipientById(UUID recipientId) {

        return notificationPreferenceRepository.findByRecipientId(recipientId)
                .orElseThrow(() -> new NullPointerException("Статуса на нотофикациите за потребител с идентификация [%s]не беше намерен".formatted(recipientId)));
    }


    public Notification sendMailNotification(NotificationRequest notificationRequest){

        UUID recipientId = notificationRequest.getRecipientId();

        NotificationPreference recipientPreference = getPreferenceByRecipientById(recipientId);

        if(!recipientPreference.isEnabled()){
            throw new IllegalArgumentException("Нотификациите за  потребител с идентификация [%s] са изключени".formatted(recipientId));
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message,false,"UTF-8");
            helper.setTo(recipientPreference.getInfo());
            helper.setSubject(notificationRequest.getTitle());
            helper.setText(notificationRequest.getContent());

            mailSender.send(message);

            Notification notification = Notification.builder()
                    .subject(notificationRequest.getTitle())
                    .content(notificationRequest.getContent())
                    .created(LocalDateTime.now())
                    .recipientId(notificationRequest.getRecipientId())
                    .status(NotificationStatus.COMPLETED)
                    .deleted(false)
                    .build();

             return notificationRepository.save(notification);
        } catch (Exception e) {
            Notification notification = Notification.builder()
                    .subject(notificationRequest.getTitle())
                    .content(notificationRequest.getContent())
                    .created(LocalDateTime.now())
                    .recipientId(notificationRequest.getRecipientId())
                    .status(NotificationStatus.FAILED)
                    .deleted(false)
                    .build();

            notificationRepository.save(notification);

            throw new RuntimeException("Изпращането беше неуспешно!",e);
        }
    }
}
