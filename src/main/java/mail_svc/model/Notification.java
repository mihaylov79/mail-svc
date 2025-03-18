package mail_svc.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column
    private UUID recipientId;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;


    @Column(nullable = false)
    private String subject;

    @Column(nullable = false,columnDefinition = "TEXT")
    private String content;

    @Column
    private LocalDateTime created;

    @Column
    @Enumerated(EnumType.STRING)
    private NotificationStatus status;

    @Column
    private boolean deleted;



}
