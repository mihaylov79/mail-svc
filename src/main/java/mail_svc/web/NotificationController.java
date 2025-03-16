package mail_svc.web;

import mail_svc.model.Notification;
import mail_svc.model.NotificationPreference;
import mail_svc.service.NotificationService;
import mail_svc.web.dto.NotificationPreferenceRequest;
import mail_svc.web.dto.NotificationPreferenceResponse;
import mail_svc.web.dto.NotificationRequest;
import mail_svc.web.dto.NotificationResponse;
import mail_svc.web.mapper.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @Autowired
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/preference")
    public ResponseEntity<NotificationPreferenceResponse> getUserNotificationStatus(@RequestParam(name ="recipientId") UUID recipientId){
        NotificationPreference preference = notificationService.getPreferenceByRecipientId(recipientId);

        NotificationPreferenceResponse responseDTO = DtoMapper.fromNotificationPreference(preference);

        return ResponseEntity.status(HttpStatus.OK).body(responseDTO);
    }


    @PostMapping("/preference")
    public ResponseEntity<NotificationPreferenceResponse>updateNotificationPreference(@RequestBody NotificationPreferenceRequest notificationPreferenceRequest){

        NotificationPreference preference = notificationService.updateNotificationPreference(notificationPreferenceRequest);
        NotificationPreferenceResponse responseDTO = DtoMapper.fromNotificationPreference(preference);

        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> sendNotification(@RequestBody NotificationRequest notificationRequest){

        Notification notification = notificationService.sendMailNotification(notificationRequest);

        NotificationResponse notificationResponse = DtoMapper.notificationResponseFromNotification(notification);

        return ResponseEntity.status(HttpStatus.CREATED).body(notificationResponse);
    }
}
