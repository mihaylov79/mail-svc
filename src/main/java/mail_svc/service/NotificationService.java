package mail_svc.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import mail_svc.model.Notification;
import mail_svc.model.NotificationPreference;
import mail_svc.model.NotificationStatus;
import mail_svc.repository.NotificationPreferenceRepository;
import mail_svc.repository.NotificationRepository;
import mail_svc.web.dto.NotificationPreferenceRequest;
import mail_svc.web.dto.NotificationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
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

    public NotificationPreference getPreferenceByRecipientId(UUID recipientId) {

        return notificationPreferenceRepository.findByRecipientId(recipientId)
                .orElseThrow(() -> new NullPointerException("Статуса на нотофикациите за потребител с идентификация [%s]не беше намерен".formatted(recipientId)));
    }

    public NotificationPreference changeNotificationPreference(UUID recipientId, boolean enabled){

        NotificationPreference recipientPreference = getPreferenceByRecipientId(recipientId);

        recipientPreference = recipientPreference.toBuilder()
                        .enabled(enabled)
                        .build();

        return notificationPreferenceRepository.save(recipientPreference);
    }


    public Notification sendMailNotification(NotificationRequest notificationRequest){

        UUID recipientId = notificationRequest.getRecipientId();

        NotificationPreference recipientPreference = getPreferenceByRecipientId(recipientId);

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

            log.warn("Изпращането до [%s] беше неуспешно - [s]!".formatted(recipientPreference.getInfo()),e.getMessage());

            return notificationRepository.save(notification);

        }
    }

    public List<Notification> getNotificationHistory(UUID recipientId){
        return notificationRepository
                .findAllByRecipientIdAndDeletedIsFalseOrderByCreatedDesc(recipientId,false, Limit.of(5));
    }

    public void resendFailed(UUID recipientId){

        NotificationPreference recipientPreference = getPreferenceByRecipientId(recipientId);

        if (!recipientPreference.isEnabled()){
            throw new IllegalArgumentException("Нотификациите за  потребител с идентификация [%s] са изключени".formatted(recipientId));
        }

        notificationRepository.findAllByRecipientIdAndStatus(recipientId,NotificationStatus.FAILED).forEach(n-> {
            try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message,false,"UTF-8");
            helper.setTo(recipientPreference.getInfo());
            helper.setSubject(n.getSubject());
            helper.setText(n.getContent());

            mailSender.send(message);

            } catch (Exception e) {
                log.warn("Изпращането до [{}] не беше успешно - [{}]",recipientPreference.getInfo(),e.getMessage());
            }
        });
    }

    public void clearNotificationHistory(UUID recipientId){

        notificationRepository
                .findAllByRecipientIdAndDeleted(recipientId,false).forEach(n-> {
                    n = n.toBuilder()
                            .deleted(true)
                            .build();
                    notificationRepository.save(n);
                });
    }
}
