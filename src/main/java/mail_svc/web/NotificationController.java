package mail_svc.web;

import mail_svc.model.NotificationPreference;
import mail_svc.service.NotificationService;
import mail_svc.web.dto.NotificationPreferenceRequest;
import mail_svc.web.dto.NotificationPreferenceResponse;
import mail_svc.web.mapper.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @Autowired
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/preference")
    public ResponseEntity<NotificationPreferenceResponse>updateNotificationPreference(@RequestBody NotificationPreferenceRequest notificationPreferenceRequest){

        NotificationPreference preference = notificationService.updateNotificationPreference(notificationPreferenceRequest);
        NotificationPreferenceResponse responseDTO = DtoMapper.fromNotificationPreference(preference);

        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }
}
