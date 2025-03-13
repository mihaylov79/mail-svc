package mail_svc.repository;

import mail_svc.model.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID>{
    Optional<NotificationPreference> findByRecipientId(UUID recipientId);
}
