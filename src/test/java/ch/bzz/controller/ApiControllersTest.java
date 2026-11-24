package ch.bzz.controller;

import ch.bzz.config.JacksonConfig;
import ch.bzz.entity.Account;
import ch.bzz.entity.Booking;
import ch.bzz.entity.Project;
import ch.bzz.exception.GlobalExceptionHandler;
import ch.bzz.repository.AccountRepository;
import ch.bzz.repository.BookingRepository;
import ch.bzz.repository.ProjectRepository;
import ch.bzz.security.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ProjectApiController.class, AccountApiController.class, BookingApiController.class})
@Import({JwtUtil.class, JacksonConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-unit-tests-must-be-at-least-32-bytes"
})
class ApiControllersTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private ProjectRepository projectRepository;

    @MockBean
    private AccountRepository accountRepository;

    @MockBean
    private BookingRepository bookingRepository;

    @Test
    void createProjectAndLoginFlow() throws Exception {
        when(projectRepository.existsById("Testverein")).thenReturn(false);

        mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"project_name\":\"Testverein\",\"password\":\"Testpasswort\"}"))
                .andExpect(status().isCreated());

        verify(projectRepository).save(any(Project.class));

        String hash = new BCryptPasswordEncoder().encode("Testpasswort");
        when(projectRepository.findById("Testverein")).thenReturn(Optional.of(new Project("Testverein", hash)));

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"project_name\":\"Testverein\",\"password\":\"Testpasswort\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isString())
                .andReturn();

        JsonNode body = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = body.get("access_token").asText();
        org.junit.jupiter.api.Assertions.assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void accountsGetAndPutCrud() throws Exception {
        Project project = new Project("Testverein", "hash");
        String token = jwtUtil.generateToken("Testverein");
        Account kasse = new Account(1L, 1000, "Kasse", project);
        Account bank = new Account(2L, 1020, "Bank", project);

        when(projectRepository.findById("Testverein")).thenReturn(Optional.of(project));
        when(accountRepository.findByProjectOrderByAccountNumberAsc(project)).thenReturn(List.of(kasse));
        when(accountRepository.findByProjectAndAccountNumber(project, 1000)).thenReturn(Optional.of(kasse));
        when(accountRepository.findByProjectAndAccountNumber(project, 1020)).thenReturn(Optional.of(bank));
        when(accountRepository.findByProjectAndAccountNumber(project, 3000)).thenReturn(Optional.empty());

        mockMvc.perform(get("/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].number").value(1000))
                .andExpect(jsonPath("$[0].name").value("Kasse"));

        String putPayload = """
                {
                  "accounts": [
                    { "number": 1000, "name": "Kasse Haupt" },
                    { "number": 1020, "name": null },
                    { "number": 3000, "name": "Mitgliederbeiträge" }
                  ]
                }
                """;

        mockMvc.perform(put("/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(putPayload))
                .andExpect(status().isOk());

        verify(accountRepository).delete(bank);
        verify(accountRepository).save(kasse);
        verify(accountRepository, times(2)).save(any(Account.class));
    }

    @Test
    void bookingsGetAndPutCrud() throws Exception {
        Project project = new Project("Testverein", "hash");
        String token = jwtUtil.generateToken("Testverein");
        Account kasse = new Account(1L, 1000, "Kasse", project);
        Account bank = new Account(2L, 1020, "Bank", project);
        Booking existing = new Booking(10L, 1, LocalDate.of(2026, 3, 15), "Bargeld abgehoben", kasse, bank, 200.0f, project);

        when(projectRepository.findById("Testverein")).thenReturn(Optional.of(project));
        when(bookingRepository.findByProjectOrderByBookingNumberAsc(project)).thenReturn(List.of(existing));
        when(bookingRepository.findByProject(project)).thenReturn(List.of(existing));
        when(bookingRepository.findByProjectAndBookingNumber(project, 1)).thenReturn(Optional.of(existing));
        when(accountRepository.findByProjectAndAccountNumber(project, 1000)).thenReturn(Optional.of(kasse));
        when(accountRepository.findByProjectAndAccountNumber(project, 1020)).thenReturn(Optional.of(bank));

        mockMvc.perform(get("/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].number").value(1))
                .andExpect(jsonPath("$[0].debit").value(1000))
                .andExpect(jsonPath("$[0].credit").value(1020));

        String putPayload = """
                {
                  "entries": [
                    { "id": null, "date": "2026-04-01", "text": "Einzahlung", "debit": 1020, "credit": 1000, "amount": 150.5 },
                    { "id": 1, "date": null, "text": null, "debit": null, "credit": null, "amount": null }
                  ]
                }
                """;

        mockMvc.perform(put("/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(putPayload))
                .andExpect(status().isOk());

        verify(bookingRepository).delete(existing);
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void unauthorizedWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/accounts"))
                .andExpect(status().isUnauthorized());
    }
}
