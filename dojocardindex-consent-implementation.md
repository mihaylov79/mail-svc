# DojoCardIndex — User Consent Implementation
## Дигитализация на хартиените споразумения

**Дата:** 2026-03-10
**Статус:** 📋 ПЛАН — Готово за имплементация
**Зависимост:** mail-svc deploy с `/parent-consent` endpoint (вж. `mail-svc-parent-consent-implementation.md`)

---

## 🎯 Цел

- Премахване на хартиените споразумения
- Всеки потребител приема споразумението дигитално при първи login
- Деца (< 18г.) → изпраща email до родител/настойник за потвърждение
- Правно валидно доказателство: кой, кога, с какво точно се е съгласил
- Admin справка при нужда (включително за съд)

---

## 🏗️ Архитектура — само необходимото

### Два нови Entity-та:

```
Agreement (текстът на споразумението — snapshot за правна валидност)
├── id (UUID)
├── title (String)                  — заглавие
├── content (TEXT)                  — пълният HTML текст (snapshot!)
├── createdAt (LocalDateTime)
└── active (boolean)                — само един е активен в даден момент

UserConsent (кой с какво се е съгласил — audit trail)
├── id (UUID)
├── user        → FK към User
├── agreement   → FK към Agreement   — старите Agreement записи не се трият (само active=false), текстът е достъпен винаги
├── agreedAt (LocalDateTime)         — кога потребителят е кликнал "Приемам"
├── isMinor (boolean)                — изчислено от birthDate при приемане
├── parentEmail (String)             — попълва се само за деца
├── consentToken (String, unique)    — 256-битов SecureRandom токен (Base64), по модела на ForgottenPasswordToken
├── tokenExpiresAt (LocalDateTime)   — agreedAt + 48h
├── parentConsentedAt (LocalDateTime)— кога родителят е потвърдил (null = чака)
├── pending (boolean, default=false) — Admin "вратичка" — потребителят може да ползва системата без дигитално съгласие
└── pendingReason (String)           — свободен текст от Admin: "Подписано хартиено споразумение", "Родител без email" и т.н.
```

### Промяна в User:
```
+ contactPersonEmail (String, nullable)   — email на родителя за деца
```

---

## 📁 Файлове за създаване/промяна

```
src/main/java/cardindex/dojocardindex/
│
├── Agreement/
│   ├── models/
│   │   ├── Agreement.java                        ✨ НОВ
│   │   └── dto/
│   │       └── AgreementRequest.java             ✨ НОВ
│   ├── repository/
│   │   └── AgreementRepository.java              ✨ НОВ
│   └── service/
│       └── AgreementService.java                 ✨ НОВ
│
├── UserConsent/
│   ├── models/
│   │   └── UserConsent.java                      ✨ НОВ
│   ├── repository/
│   │   └── UserConsentRepository.java            ✨ НОВ
│   └── service/
│       └── UserConsentService.java               ✨ НОВ
│
├── notification/
│   └── client/
│       ├── dto/
│       │   └── ParentConsentRequest.java         ✨ НОВ
│       └── NotificationClient.java               ✏️ ПРОМЯНА — +1 метод
│
├── config/
│   ├── SecurityConfig.java                       ✏️ ПРОМЯНА — +permitAll + custom SuccessHandler
│   └── AuthenticationSuccessListener.java        ✏️ ПРОМЯНА — +consent check → redirect
│
├── web/
│   ├── AgreementConsentController.java           ✨ НОВ
│   └── ParentConsentController.java              ✨ НОВ
│
└── User/
    └── models/
        └── User.java                             ✏️ ПРОМЯНА — +contactPersonEmail

src/main/resources/templates/
├── consent-show.html                             ✨ НОВ
├── consent-pending-parent.html                   ✨ НОВ
├── parent-consent-verify.html                    ✨ НОВ
└── parent-consent-success.html                   ✨ НОВ
```

---

## 1. Entity — `Agreement.java`

```java
package cardindex.dojocardindex.Agreement.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agreements")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Agreement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;          // Пълният HTML текст — snapshot за правна валидност

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private boolean active;          // Само един е active = true в даден момент
}
```

---

## 2. Repository — `AgreementRepository.java`

```java
package cardindex.dojocardindex.Agreement.repository;

import cardindex.dojocardindex.Agreement.models.Agreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgreementRepository extends JpaRepository<Agreement, UUID> {

    Optional<Agreement> findByActiveTrue();   // Взема текущото активно споразумение
}
```

---

## 3. DTO — `AgreementRequest.java`

```java
package cardindex.dojocardindex.Agreement.models.dto;

import lombok.Data;

@Data
public class AgreementRequest {
    private String title;
    private String content;
}
```

---

## 4. Service — `AgreementService.java`

```java
package cardindex.dojocardindex.Agreement.service;

import cardindex.dojocardindex.Agreement.models.Agreement;
import cardindex.dojocardindex.Agreement.models.dto.AgreementRequest;
import cardindex.dojocardindex.Agreement.repository.AgreementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AgreementService {

    private final AgreementRepository agreementRepository;

    public AgreementService(AgreementRepository agreementRepository) {
        this.agreementRepository = agreementRepository;
    }

    // Взема текущото активно споразумение — хвърля exception ако няма
    public Agreement getActiveAgreement() {
        return agreementRepository.findByActiveTrue()
                .orElseThrow(() -> new RuntimeException("Няма активно споразумение в системата!"));
    }

    // Admin създава ново споразумение като draft (active = false)
    // Може да се редактира преди публикуване
    @Transactional
    public Agreement createDraft(AgreementRequest request) {
        return agreementRepository.save(Agreement.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .createdAt(LocalDateTime.now())
                .active(false)
                .build());
    }

    // Admin публикува draft → той става активен, старото се деактивира
    // Всички потребители без UserConsent ще видят новото при следващ login
    @Transactional
    public Agreement publish(UUID agreementId) {
        Agreement toPublish = agreementRepository.findById(agreementId)
                .orElseThrow(() -> new RuntimeException("Споразумението не е намерено!"));

        if (toPublish.isActive()) {
            throw new RuntimeException("Споразумението вече е активно!");
        }

        // Деактивираме старото активно
        agreementRepository.findByActiveTrue().ifPresent(old ->
                agreementRepository.save(old.toBuilder().active(false).build()));

        // Публикуваме новото
        return agreementRepository.save(toPublish.toBuilder().active(true).build());
    }
}
```

---

## 4. Entity — `UserConsent.java`

```java
package cardindex.dojocardindex.UserConsent.models;

import cardindex.dojocardindex.Agreement.models.Agreement;
import cardindex.dojocardindex.User.models.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_consents",
        indexes = {
            @Index(name = "idx_user_consent_user", columnList = "user_id"),
            @Index(name = "idx_user_consent_token", columnList = "consent_token")
        })
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "agreement_id", nullable = false)
    private Agreement agreement;             // FK към Agreement — текстът е там, старите записи не се трият

    @Column(name = "agreed_at")
    private LocalDateTime agreedAt;          // Кога потребителят е кликнал "Приемам"

    @Column(name = "is_minor", nullable = false)
    private boolean isMinor;                 // Изчислено от birthDate при приемане

    @Column(name = "parent_email")
    private String parentEmail;              // Email на родителя (само за деца)

    @Column(name = "consent_token", unique = true)
    private String consentToken;             // UUID токен за линка в имейла

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;    // agreedAt + 48h

    @Column(name = "parent_consented_at")
    private LocalDateTime parentConsentedAt; // Кога родителят е потвърдил

    @Column(nullable = false)
    private boolean pending;                 // Admin "вратичка" — временно отложено

    @Column(name = "pending_reason")
    private String pendingReason;            // "Родител без email", "Legacy import" etc.

    // Helper — дали съгласието е напълно валидно
    public boolean isFullyConsented() {
        // pending = true → треньорът е разрешил временно (чака родителско потвърждение)
        // След като родителят потвърди → pending = false, parentConsentedAt се попълва
        if (pending) return true;
        if (!isMinor) return agreedAt != null;
        return agreedAt != null && parentConsentedAt != null;
    }

    // Helper — дали токенът за родителя все още е валиден
    public boolean isTokenValid() {
        return tokenExpiresAt != null && LocalDateTime.now().isBefore(tokenExpiresAt);
    }
}
```

---

## 5. Repository — `UserConsentRepository.java`

```java
package cardindex.dojocardindex.UserConsent.repository;

import cardindex.dojocardindex.User.models.User;
import cardindex.dojocardindex.UserConsent.models.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {

    Optional<UserConsent> findByUser(User user);
    Optional<UserConsent> findByConsentToken(String token);
    List<UserConsent> findAllByOrderByAgreedAtDesc();                        // Всички — пълна справка
    List<UserConsent> findAllByPendingTrueOrderByAgreedAtDesc();             // Само pending — Admin проверява дали родителят е потвърдил
}
```

---

## 6. Service — `UserConsentService.java`

```java
package cardindex.dojocardindex.UserConsent.service;

import cardindex.dojocardindex.Agreement.models.Agreement;
import cardindex.dojocardindex.Agreement.service.AgreementService;
import cardindex.dojocardindex.User.models.User;
import cardindex.dojocardindex.User.models.UserStatus;
import cardindex.dojocardindex.User.service.UserService;
import cardindex.dojocardindex.UserConsent.models.UserConsent;
import cardindex.dojocardindex.UserConsent.repository.UserConsentRepository;
import cardindex.dojocardindex.notification.client.NotificationClient;
import cardindex.dojocardindex.notification.client.dto.ParentConsentRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Base64;
import java.util.List;

@Service
public class UserConsentService {

    private final UserConsentRepository userConsentRepository;
    private final AgreementService agreementService;
    private final NotificationClient notificationClient;
    private final UserService userService;

    public UserConsentService(UserConsentRepository userConsentRepository,
                              AgreementService agreementService,
                              NotificationClient notificationClient,
                              UserService userService) {
        this.userConsentRepository = userConsentRepository;
        this.agreementService = agreementService;
        this.notificationClient = notificationClient;
        this.userService = userService;
    }

    // ── Проверка при login ──────────────────────────────────────────────────
    // Извиква се от AuthenticationSuccessListener — резултатът се пази в session
    public boolean hasValidConsent(User user) {
        return userConsentRepository.findByUser(user)
                .map(UserConsent::isFullyConsented)
                .orElse(false);
    }

    // ── Пълнолетен приема директно ─────────────────────────────────────────
    @Transactional
    public void acceptDirectConsent(User user) {
        Agreement agreement = agreementService.getActiveAgreement();

        UserConsent consent = UserConsent.builder()
                .user(user)
                .agreement(agreement)
                .agreedAt(LocalDateTime.now())
                .isMinor(false)
                .pending(false)
                .build();

        userConsentRepository.save(consent);
    }

    // ── Дете приема → изпраща email до родителя ────────────────────────────
    @Transactional
    public void initiateParentConsent(User user, String baseUrl) {
        Agreement agreement = agreementService.getActiveAgreement();
        String token = generateSecureToken();   // 256-битов токен — по модела на ForgottenPasswordToken

        UserConsent consent = UserConsent.builder()
                .user(user)
                .agreement(agreement)
                .agreedAt(LocalDateTime.now())
                .isMinor(true)
                .parentEmail(user.getContactPersonEmail())
                .consentToken(token)
                .tokenExpiresAt(LocalDateTime.now().plusHours(48))
                .pending(false)
                .build();

        userConsentRepository.save(consent);

        // Изпраща email до родителя чрез mail-svc
        ParentConsentRequest emailRequest = ParentConsentRequest.builder()
                .parentEmail(user.getContactPersonEmail())
                .childFirstName(user.getFirstName())
                .childLastName(user.getLastName())
                .consentLink(baseUrl + "/parent-consent/verify?token=" + token)
                .build();

        notificationClient.sendParentConsentEmail(emailRequest);
    }

    // ── Родителят кликва линка и потвърждава ───────────────────────────────
    @Transactional
    public UserConsent verifyParentConsent(String token) {
        UserConsent consent = userConsentRepository.findByConsentToken(token)
                .orElseThrow(() -> new RuntimeException("Невалиден токен!"));

        if (!consent.isTokenValid()) {
            throw new RuntimeException("Токенът е изтекъл! Моля свържете се с администратора.");
        }

        // Ако детето е на pending (тренира с разрешение на треньора докато чака) →
        // при потвърждение от родителя pending = false, вече има реално дигитално съгласие
        return userConsentRepository.save(consent.toBuilder()
                .parentConsentedAt(LocalDateTime.now())
                .pending(false)
                .pendingReason(null)
                .build());
    }

    // ── Прегенериране на токен при изтичане ───────────────────────────────
    // Извиква се от детето при login ако токенът е изтекъл и родителят още не е потвърдил
    @Transactional
    public void regenerateParentConsentToken(User user, String baseUrl) {
        UserConsent consent = userConsentRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Няма намерено съгласие за потребителя!"));

        if (consent.getParentConsentedAt() != null) {
            throw new RuntimeException("Родителят вече е потвърдил — не е нужен нов токен!");
        }

        String newToken = generateSecureToken();

        UserConsent updated = consent.toBuilder()
                .consentToken(newToken)
                .tokenExpiresAt(LocalDateTime.now().plusHours(48))
                .build();

        userConsentRepository.save(updated);

        // Изпраща нов email до родителя
        ParentConsentRequest emailRequest = ParentConsentRequest.builder()
                .parentEmail(consent.getParentEmail())
                .childFirstName(user.getFirstName())
                .childLastName(user.getLastName())
                .consentLink(baseUrl + "/parent-consent/verify?token=" + newToken)
                .build();

        notificationClient.sendParentConsentEmail(emailRequest);
    }

    // ── Потребителят отказва → веднага деактивация ─────────────────────────
    @Transactional
    public void refuseConsent(User user) {
        // Деактивираме акаунта
        User deactivated = user.toBuilder()
                .status(UserStatus.INACTIVE)
                .build();
        userService.saveUser(deactivated);
    }

    // ── Admin вратичка — временно отлагане за деца без родителски email ────
    @Transactional
    public void createPendingConsent(User user, String reason) {
        Agreement agreement = agreementService.getActiveAgreement();

        UserConsent consent = UserConsent.builder()
                .user(user)
                .agreement(agreement)
                .agreedAt(LocalDateTime.now())
                .isMinor(true)
                .pending(true)
                .pendingReason(reason)
                .build();

        userConsentRepository.save(consent);
    }

    // ── Admin справка — всички съгласия ────────────────────────────────────
    public List<UserConsent> getAllConsents() {
        return userConsentRepository.findAllByOrderByAgreedAtDesc();
    }

    // ── Admin справка — само pending (чакат родителско потвърждение) ────────
    // Admin проверява тук дали родителят е потвърдил и може да маха pending ако е нужно
    public List<UserConsent> getPendingConsents() {
        return userConsentRepository.findAllByPendingTrueOrderByAgreedAtDesc();
    }

    // ── Helper — проверка дали потребителят е непълнолетен ─────────────────
    public boolean isMinor(User user) {
        if (user.getBirthDate() == null) return false;
        return Period.between(user.getBirthDate(), LocalDate.now()).getYears() < 18;
    }

    // ── Helper — генериране на токен (256-битов, по модела на ForgottenPasswordToken) ─
    private String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
}
```

---

## 7. DTO — `ParentConsentRequest.java`

```java
package cardindex.dojocardindex.notification.client.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParentConsentRequest {

    private String parentEmail;
    private String childFirstName;
    private String childLastName;
    private String consentLink;
}
```

---

## 8. NotificationClient — добавяме 1 метод

```java
// В съществуващия NotificationClient.java добавяме:

@PostMapping("/parent-consent")
ResponseEntity<Void> sendParentConsentEmail(@RequestBody ParentConsentRequest request);
```

---

## 9. User.java — добавяме 1 поле

```java
// В съществуващия User.java добавяме:

@Column(name = "contact_person_email")
private String contactPersonEmail;    // Email на родителя — nullable, само за деца

// + getter:
public String getContactPersonEmail() {
    return contactPersonEmail;
}
```

---

## 10. AuthenticationSuccessListener — проверка при login, redirect ако няма consent

Логиката е проста: проверяваме **веднъж при login**. Ако има consent → влиза в `/home` и ползва приложението нормално до logout. Ако няма → пренасочваме към `/consent/show`. Нищо не се пази в session.
<br/>
```java
package cardindex.dojocardindex.config;

import cardindex.dojocardindex.User.models.User;
import cardindex.dojocardindex.User.service.UserService;
import cardindex.dojocardindex.UserConsent.service.UserConsentService;
import cardindex.dojocardindex.notification.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuthenticationSuccessListener {

    private final NotificationService notificationService;
    private final UserService userService;
    private final UserConsentService userConsentService;

    public AuthenticationSuccessListener(NotificationService notificationService,
                                         UserService userService,
                                         UserConsentService userConsentService) {
        this.notificationService = notificationService;
        this.userService = userService;
        this.userConsentService = userConsentService;
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        String email = event.getAuthentication().getName();
        User user = userService.findUserByEmail(email);

        notificationService.checkNotificationPreference(user.getId(), user.getEmail());

        // Ако няма consent → слагаме redirect destination в session.
        // Custom SuccessHandler в SecurityConfig го прочита, пренасочва и го маха веднага.
        // Нищо не остава в session след redirect-а.
        if (!userConsentService.hasValidConsent(user)) {
            HttpSession session = ((ServletRequestAttributes)
                    RequestContextHolder.currentRequestAttributes())
                    .getRequest().getSession();
            session.setAttribute("redirectAfterLogin", "/consent/show");
        }
    }
}
```

---

## 11. SecurityConfig — custom SuccessHandler + permitAll

```java
// В съществуващия securityFilterChain:

.formLogin(formLogin -> formLogin
        .loginPage("/login")
        .usernameParameter("email")
        .passwordParameter("password")
        .successHandler((request, response, authentication) -> {
            // Listener е сложил redirect само ако няма consent
            HttpSession session = request.getSession(false);
            String redirect = (session != null)
                    ? (String) session.getAttribute("redirectAfterLogin")
                    : null;
            if (redirect != null) {
                session.removeAttribute("redirectAfterLogin");  // Веднага се маха
                response.sendRedirect(redirect);                // → /consent/show
            } else {
                response.sendRedirect("/home");                 // → нормален вход
            }
        })
        .failureUrl("/login?error")
        .permitAll()
)

// Добавяме към requestMatchers:
.requestMatchers("/parent-consent/**").permitAll()   // Родителят няма акаунт!
.requestMatchers("/consent/**").authenticated()
```

---

## 14. AgreementConsentController.java

```java
package cardindex.dojocardindex.web;

import cardindex.dojocardindex.Agreement.service.AgreementService;
import cardindex.dojocardindex.User.models.User;
import cardindex.dojocardindex.User.service.UserService;
import cardindex.dojocardindex.UserConsent.repository.UserConsentRepository;
import cardindex.dojocardindex.UserConsent.service.UserConsentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/consent")
public class AgreementConsentController {

    private final AgreementService agreementService;
    private final UserConsentService userConsentService;
    private final UserConsentRepository userConsentRepository;
    private final UserService userService;

    public AgreementConsentController(AgreementService agreementService,
                                      UserConsentService userConsentService,
                                      UserConsentRepository userConsentRepository,
                                      UserService userService) {
        this.agreementService = agreementService;
        this.userConsentService = userConsentService;
        this.userConsentRepository = userConsentRepository;
        this.userService = userService;
    }

    // Показва текста на споразумението
    @GetMapping("/show")
    public ModelAndView showConsent() {
        ModelAndView mav = new ModelAndView("consent-show");
        mav.addObject("agreement", agreementService.getActiveAgreement());
        return mav;
    }

    // Потребителят приема
    @PostMapping("/accept")
    public String acceptConsent(@AuthenticationPrincipal UserDetails userDetails,
                                HttpServletRequest request) {
        User user = userService.findUserByEmail(userDetails.getUsername());

        if (userConsentService.isMinor(user)) {
            // Дете — проверяваме дали има contactPersonEmail
            if (user.getContactPersonEmail() == null || user.getContactPersonEmail().isBlank()) {
                return "redirect:/consent/no-parent-email";
            }
            String baseUrl = request.getScheme() + "://" + request.getServerName()
                             + ":" + request.getServerPort();
            userConsentService.initiateParentConsent(user, baseUrl);
            return "redirect:/consent/pending-parent";
        }

        // Пълнолетен — директно приемане
        userConsentService.acceptDirectConsent(user);
        return "redirect:/home";
    }

    // Потребителят отказва
    @PostMapping("/refuse")
    public String refuseConsent(@AuthenticationPrincipal UserDetails userDetails,
                                HttpSession session) {
        User user = userService.findUserByEmail(userDetails.getUsername());
        userConsentService.refuseConsent(user);
        session.invalidate();
        return "redirect:/login?consentRefused";
    }

    // Страница "Изчакайте потвърждение от родител"
    // При всяко опресняване проверява дали родителят вече е потвърдил
    @GetMapping("/pending-parent")
    public ModelAndView pendingParent(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findUserByEmail(userDetails.getUsername());
        ModelAndView mav = new ModelAndView("consent-pending-parent");

        userConsentRepository.findByUser(user).ifPresent(c -> {
            // Родителят вече е потвърдил → redirect към /home
            if (c.getParentConsentedAt() != null) {
                mav.setViewName("redirect:/home");
                return;
            }
            // Подаваме дали токенът е изтекъл — template показва бутон само тогава
            mav.addObject("tokenExpired", !c.isTokenValid());
        });

        return mav;
    }

    // Прегенериране на токен — само ако е изтекъл и родителят още не е потвърдил
    @PostMapping("/resend-parent-consent")
    public String resendParentConsent(@AuthenticationPrincipal UserDetails userDetails,
                                      HttpServletRequest request) {
        User user = userService.findUserByEmail(userDetails.getUsername());
        String baseUrl = request.getScheme() + "://" + request.getServerName()
                         + ":" + request.getServerPort();
        userConsentService.regenerateParentConsentToken(user, baseUrl);
        return "redirect:/consent/pending-parent";
    }

    // Страница "Няма родителски email"
    @GetMapping("/no-parent-email")
    public String noParentEmail() {
        return "consent-no-parent-email";
    }
}
```

---

## 13. ParentConsentController.java

```java
package cardindex.dojocardindex.web;

import cardindex.dojocardindex.Agreement.models.Agreement;
import cardindex.dojocardindex.UserConsent.models.UserConsent;
import cardindex.dojocardindex.UserConsent.service.UserConsentService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/parent-consent")
public class ParentConsentController {

    private final UserConsentService userConsentService;

    public ParentConsentController(UserConsentService userConsentService) {
        this.userConsentService = userConsentService;
    }

    // Родителят отваря линка — вижда текста на споразумението
    @GetMapping("/verify")
    public ModelAndView showParentConsent(@RequestParam("token") String token) {
        UserConsent consent = userConsentService.findByToken(token);  // хвърля ако невалиден

        ModelAndView mav = new ModelAndView("parent-consent-verify");
        mav.addObject("consent", consent);
        mav.addObject("agreement", consent.getAgreement());
        mav.addObject("token", token);
        return mav;
    }

    // Родителят потвърждава
    @PostMapping("/confirm")
    public String confirmParentConsent(@RequestParam("token") String token) {
        userConsentService.verifyParentConsent(token);
        return "redirect:/parent-consent/success";
    }

    @GetMapping("/success")
    public String success() {
        return "parent-consent-success";
    }
}
```

> **Забележка:** Добавяме `findByToken()` в `UserConsentService`:
> ```java
> public UserConsent findByToken(String token) {
>     return userConsentRepository.findByConsentToken(token)
>             .orElseThrow(() -> new RuntimeException("Невалиден или изтекъл токен!"));
> }
> ```

---

## 14. Thymeleaf Templates

### `consent-show.html`
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head><title>Споразумение — Dragon Dojo</title></head>
<body>
  <h2 th:text="${agreement.title}">Споразумение</h2>

  <div th:utext="${agreement.content}">Текст на споразумението...</div>

  <form th:action="@{/consent/accept}" method="post">
    <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
    <label>
      <input type="checkbox" id="agreed" required/>
      Прочетох и приемам споразумението
    </label>
    <br/>
    <button type="submit">Приемам</button>
  </form>

  <form th:action="@{/consent/refuse}" method="post">
    <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
    <button type="submit"
            onclick="return confirm('Отказвайки споразумението акаунтът Ви ще бъде деактивиран. Продължавате?')">
      Отказвам
    </button>
  </form>
</body>
</html>
```

### `consent-pending-parent.html`
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head><title>Изчакване на родителско съгласие</title></head>
<body>
  <h2>Изпратихме имейл на родителя/настойника</h2>

  <p>За да завърши регистрацията, родителят Ви трябва да потвърди споразумението
     чрез линка в имейла.</p>

  <!-- Показва се само ако токенът НЕ е изтекъл -->
  <div th:if="${!tokenExpired}">
    <p>Линкът е валиден <strong>48 часа</strong>.</p>
    <p>След като родителят потвърди, натиснете бутона по-долу за да влезете.</p>
    <!-- Просто GET към същата страница — контролерът ще redirect-не към /home ако е потвърдено -->
    <a th:href="@{/consent/pending-parent}">
      <button type="button">Провери статус</button>
    </a>
  </div>

  <!-- Показва се само ако токенът Е изтекъл -->
  <div th:if="${tokenExpired}">
    <p style="color: red;">Линкът е изтекъл. Изпратете нов на родителя/настойника.</p>
    <form th:action="@{/consent/resend-parent-consent}" method="post">
      <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
      <button type="submit">Изпрати нов линк</button>
    </form>
  </div>

  <a th:href="@{/logout}">Изход</a>
</body>
</html>
```

### `parent-consent-verify.html`
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head><title>Родителско съгласие — Dragon Dojo</title></head>
<body>
  <h2>Родителско/Настойническо съгласие</h2>
  <p>Детето <strong th:text="${consent.user.firstName + ' ' + consent.user.lastName}"></strong>
     се регистрира в системата на Dragon Dojo.</p>

  <h3 th:text="${agreement.title}">Споразумение</h3>
  <div th:utext="${agreement.content}">Текст...</div>

  <form th:action="@{/parent-consent/confirm}" method="post">
    <input type="hidden" name="token" th:value="${token}"/>
    <label>
      <input type="checkbox" required/>
      Прочетох и давам своето родителско/настойническо съгласие
    </label>
    <br/>
    <button type="submit">Потвърждавам</button>
  </form>
</body>
</html>
```

### `parent-consent-success.html`
```html
<!DOCTYPE html>
<html>
<head><title>Потвърдено — Dragon Dojo</title></head>
<body>
  <h2>Благодарим!</h2>
  <p>Родителското съгласие е записано успешно.</p>
  <p>Детето Ви вече може да влезе в системата на Dragon Dojo.</p>
</body>
</html>
```

---

## 15. Admin справка — в UserController или отделен AdminConsentController

```java
// GET /admin/consents — таблица с всички съгласия за справка/съд
@GetMapping("/admin/consents")
@PreAuthorize("hasRole('ADMIN')")
public ModelAndView allConsents() {
    ModelAndView mav = new ModelAndView("admin-consents");
    mav.addObject("consents", userConsentService.getAllConsents());
    return mav;
}
```

---

## ✅ Ред на имплементация

```
Стъпка 1 — Entities & Repository (30 мин)
  а) Agreement.java
  б) AgreementRepository.java
  в) UserConsent.java
  г) UserConsentRepository.java

Стъпка 2 — User промяна (5 мин)
  а) User.java → добави contactPersonEmail + getter

Стъпка 3 — Services (45 мин)
  а) AgreementService.java
  б) UserConsentService.java (вкл. findByToken метод)

Стъпка 4 — Notification (10 мин)
  а) ParentConsentRequest.java DTO
  б) NotificationClient.java → +1 метод

Стъпка 5 — Security & Login redirect (20 мин)
  а) SecurityConfig.java → +custom SuccessHandler + permitAll за /parent-consent/**
  б) AuthenticationSuccessListener.java → +consent check → session redirect flag

Стъпка 6 — Controllers (30 мин)
  а) AgreementConsentController.java
  б) ParentConsentController.java

Стъпка 7 — Templates (30 мин)
  а) consent-show.html
  б) consent-pending-parent.html
  в) parent-consent-verify.html
  г) parent-consent-success.html

Стъпка 8 — Seed данни (10 мин)
  а) Първо споразумение в БД чрез CommandLineRunner или SQL script
     (без данни в agreements таблицата нищо не работи!)
```

---

## ⚠️ Важни забележки

1. **Seed на Agreement** — преди старт трябва поне 1 запис с `active = true` в `agreements` таблицата, иначе при всеки login ще гърми exception.

2. **Проверката е само при login** — consent се проверява веднъж при login. Ако Admin анулира consent на жив потребител, ефектът влиза в сила при следващия logout/login. За клуб от малък мащаб това е напълно приемливо.

3. **`/parent-consent/**` без authentication** — родителят няма акаунт в системата, затова endpoint-ът е `permitAll`. CSRF токен не се праща в имейла → `parent-consent/confirm` трябва да е exempted от CSRF или да ползва GET (обсъди).

4. **Existing users** — при първи login след deploy всички без `UserConsent` запис ще видят `/consent/show`. Това е желаното поведение — gradual migration без downtime.

5. **contactPersonEmail** — задължителен само за деца при runtime. Ако Admin не го е попълнил, детето се redirect-ва към `/consent/no-parent-email` с инструкция да се свърже с администратора.

6. **`contactPersonEmail` — само Admin може да го променя.** Потребителят няма достъп до това поле. Това укрепва правната позиция:
   - Родителят физически е дошъл в клуба с детето при регистрация
   - Admin лично е въвел родителския email
   - Системата е изпратила линк на този email и родителят го е потвърдил
   - Резултат: двустепенна верификация — физическо присъствие + електронно потвърждение
   - Съвпадането на `userEmail` и `contactPersonEmail` е напълно нормално за малки деца и не е правен риск при тази схема.

---

*Документ създаден: 2026-03-10*

