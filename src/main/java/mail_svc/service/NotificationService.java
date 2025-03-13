package mail_svc.service;

import mail_svc.model.NotificationPreference;
import mail_svc.repository.NotificationPreferenceRepository;
import mail_svc.repository.NotificationRepository;
import mail_svc.web.dto.NotificationPreferenceRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;

    @Autowired
    public NotificationService(NotificationRepository notificationRepository, NotificationPreferenceRepository notificationPreferenceRepository) {
        this.notificationRepository = notificationRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
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
}
