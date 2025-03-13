package mail_svc.web.mapper;

import lombok.experimental.UtilityClass;
import mail_svc.model.NotificationPreference;
import mail_svc.web.dto.NotificationPreferenceResponse;

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
}
