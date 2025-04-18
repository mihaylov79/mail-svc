package mail_svc.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import mail_svc.model.ForgottenPasswordRequest;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Autowired
    public NotificationService(NotificationRepository notificationRepository, NotificationPreferenceRepository notificationPreferenceRepository, JavaMailSender mailSender, SpringTemplateEngine templateEngine) {
        this.notificationRepository = notificationRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
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
                .orElseThrow(() -> new NullPointerException("Статуса на извстията за потребител с идентификация [%s] не беше намерен".formatted(recipientId)));
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
            throw new IllegalArgumentException("Известията за  потребител с идентификация [%s] са изключени".formatted(recipientId));
        }

        try {

            Context context = new Context();
            context.setVariable("firstName",notificationRequest.getFirstName());
            context.setVariable("lastName",notificationRequest.getLastName());
            context.setVariable("content",notificationRequest.getContent());


            String htmlContent = templateEngine.process("mail",context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message,true,"UTF-8");
            helper.setTo(recipientPreference.getInfo());
            helper.setSubject(notificationRequest.getTitle());
            helper.setText(htmlContent,true);

            mailSender.send(message);

            Notification notification = Notification.builder()
                    .subject(notificationRequest.getTitle())
                    .content(notificationRequest.getContent())
                    .firstName(notificationRequest.getFirstName())
                    .lastName(notificationRequest.getLastName())
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
                    .firstName(notificationRequest.getFirstName())
                    .lastName(notificationRequest.getLastName())
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
                .findAllByRecipientIdAndDeletedIsFalseOrderByCreatedDesc(recipientId, Limit.of(5));
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

    public void sendForgottenPasswordLink(ForgottenPasswordRequest request){

        Context context = new Context();
        context.setVariable("content",request.getContent());

        String htmlContent = templateEngine.process("forgotten-password",context);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message,true,"UTF-8");
            helper.setTo(request.getRecipient());
            helper.setSubject(request.getTitle());
            helper.setText(htmlContent,true);
            mailSender.send(message);

        } catch (MessagingException e) {

            log.warn("Изпращането на връзка за възстановяване на забравена парола до {} беше неуспешно!",request.getRecipient(),e);
            throw new RuntimeException("мейл с връзка за възстановяването на парола не беше изпратен!");
        }


    }

    @Transactional
    @Scheduled(cron = "0 0 0 1 * ?") // На всяко 1-во число от месеца
    public void cleanUpOldNotifications() {
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);

        int deletedCount = notificationRepository.deleteByDeletedTrueAndCreatedBefore(threeMonthsAgo);

        if (deletedCount > 0) {
            log.info("Изтрити {} стари нотификации (преди {}).", deletedCount, threeMonthsAgo);
        } else {
            log.info("Няма стари нотификации за изтриване (преди {}).", threeMonthsAgo);
        }
    }


}
