package mail_svc.repository;

import mail_svc.model.Notification;
import mail_svc.model.NotificationStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {




    List<Notification> findAllByRecipientIdAndDeletedIsFalseOrderByCreatedDesc(UUID recipientId, Limit limit);

    List<Notification> findAllByRecipientIdAndStatus(UUID recipientId, NotificationStatus status);

    List<Notification> findAllByRecipientIdAndDeleted(UUID recipientId, boolean deleted);
}