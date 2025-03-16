package mail_svc.web.mapper;

import lombok.experimental.UtilityClass;
                  import mail_svc.model.Notification;
import mail_svc.model.NotificationPreference;
import mail_svc.web.dto.NotificationPreferenceResponse;
import mail_svc.web.dto.NotificationResponse;

import javax.swing.text.html.parser.Entity;
import java.time.LocalDateTime;

@UtilityClass
public class DtoMapper {

    public static NotificationPreferenceResponse fromNotificationPreference(NotificationPreference entity){

       return NotificationPreferenceResponse.builder()
               .id(entity.getId())
               .recipientId(entity.getRecipientId())
               .enabled(entity.isEnabled())
               .info(entity.getInfo())
               .build();
    }

    public static NotificationResponse notificationResponseFromNotification(Notification notification){

           return NotificationResponse.builder()
                   .title(notification.getSubject())
                   .content(notification.getContent())
                   .status(notification.getStatus())
                   .created(LocalDateTime.now())
                   .build();
    }
}
