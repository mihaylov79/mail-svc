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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @Autowired
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponse> getUserNotificationStatus(@RequestParam(name ="recipientId") UUID recipientId){
        NotificationPreference preference = notificationService.getPreferenceByRecipientId(recipientId);

        NotificationPreferenceResponse responseDTO = DtoMapper.fromNotificationPreference(preference);

        return ResponseEntity.status(HttpStatus.OK).body(responseDTO);
    }

    @PostMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponse>updateNotificationPreference(@RequestBody NotificationPreferenceRequest notificationPreferenceRequest){

        NotificationPreference preference = notificationService.updateNotificationPreference(notificationPreferenceRequest);
        NotificationPreferenceResponse responseDTO = DtoMapper.fromNotificationPreference(preference);

        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }

    @PutMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponse>changeNotificationPreferenceStatus(@RequestParam(name = "recipientId") UUID recipientId,
                                                                                            @RequestParam(name = "enabled") boolean enabled){
        NotificationPreference notificationPreference = notificationService.changeNotificationPreference(recipientId,enabled);

        NotificationPreferenceResponse responseDto = DtoMapper.fromNotificationPreference(notificationPreference);

        return ResponseEntity.status(HttpStatus.OK).body(responseDto);

    }

    @PostMapping
    public ResponseEntity<NotificationResponse> sendNotification(@RequestBody NotificationRequest notificationRequest){

        Notification notification = notificationService.sendMailNotification(notificationRequest);

        NotificationResponse notificationResponse = DtoMapper.notificationResponseFromNotification(notification);

        return ResponseEntity.status(HttpStatus.CREATED).body(notificationResponse);
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotificationHistory(@RequestParam(name = "recipientId") UUID recipientId){

        List<NotificationResponse> notificationHistory = notificationService
                .getNotificationHistory(recipientId).stream()
                .map(DtoMapper::notificationResponseFromNotification).toList();

        return ResponseEntity.status(HttpStatus.OK).body(notificationHistory);
    }

    @DeleteMapping
    public ResponseEntity<Void>clearHistory(@RequestParam(name = "recipientId") UUID recipientId){

        notificationService.clearNotificationHistory(recipientId);

        return ResponseEntity.status(HttpStatus.OK).body(null);

    }

}
