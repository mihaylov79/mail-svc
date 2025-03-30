package mail_svc.web;

import mail_svc.model.NotificationPreference;
import mail_svc.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
public class NotificationControllerApiTest {

    @MockitoBean
    private NotificationService notificationService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void  getUserNotificationStatus_happyPath() throws Exception {
        NotificationPreference preference = NotificationPreference.builder()
                .id(UUID.randomUUID())
                .recipientId(UUID.randomUUID())
                .info("notify@test.com")
                .created(LocalDateTime.now())
                .updated(LocalDateTime.now())
                .build();

        when(notificationService.getPreferenceByRecipientId(any())).thenReturn(preference);
        MockHttpServletRequestBuilder request =
                get("/api/v1/notifications/preferences")
                        .param("recipientId", UUID.randomUUID().toString());


        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("id").isNotEmpty())
                .andExpect(jsonPath("recipientId").isNotEmpty())
                .andExpect(jsonPath("enabled").isNotEmpty())
                .andExpect(jsonPath("info").isNotEmpty());
    }


}
