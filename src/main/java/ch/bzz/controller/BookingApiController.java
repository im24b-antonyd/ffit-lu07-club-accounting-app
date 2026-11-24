package ch.bzz.controller;

import ch.bzz.entity.Account;
import ch.bzz.entity.Project;
import ch.bzz.generated.api.BookingApi;
import ch.bzz.generated.model.Booking;
import ch.bzz.generated.model.BookingUpdate;
import ch.bzz.generated.model.UpdateBookingsRequest;
import ch.bzz.repository.AccountRepository;
import ch.bzz.repository.BookingRepository;
import ch.bzz.repository.ProjectRepository;
import ch.bzz.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
public class BookingApiController implements BookingApi {

    private final BookingRepository bookingRepository;
    private final AccountRepository accountRepository;
    private final ProjectRepository projectRepository;
    private final JwtUtil jwtUtil;
    private final HttpServletRequest request;

    public BookingApiController(
            BookingRepository bookingRepository,
            AccountRepository accountRepository,
            ProjectRepository projectRepository,
            JwtUtil jwtUtil,
            HttpServletRequest request
    ) {
        this.bookingRepository = bookingRepository;
        this.accountRepository = accountRepository;
        this.projectRepository = projectRepository;
        this.jwtUtil = jwtUtil;
        this.request = request;
    }

    @Override
    public ResponseEntity<List<Booking>> getBookings() {
        Project project = getAuthenticatedProject();
        List<Booking> bookings = bookingRepository.findByProjectOrderByBookingNumberAsc(project)
                .stream()
                .map(entity -> new Booking(
                        entity.getBookingNumber(),
                        entity.getDate(),
                        entity.getText(),
                        entity.getDebitAccount().getAccountNumber(),
                        entity.getCreditAccount().getAccountNumber(),
                        entity.getAmount()
                ))
                .toList();
        log.debug("Returning {} bookings for project '{}'", bookings.size(), project.getProjectName());
        return ResponseEntity.ok(bookings);
    }

    @Override
    @Transactional
    public ResponseEntity<Void> updateBookings(UpdateBookingsRequest updateBookingsRequest) {
        Project project = getAuthenticatedProject();
        List<BookingUpdate> entries = updateBookingsRequest.getEntries();
        if (entries == null) {
            return ResponseEntity.ok().build();
        }

        int maxBookingNumber = bookingRepository.findByProject(project).stream()
                .mapToInt(ch.bzz.entity.Booking::getBookingNumber)
                .max()
                .orElse(0);

        for (BookingUpdate entry : entries) {
            LocalDate date = unwrap(entry.getDate());
            String text = unwrap(entry.getText());
            Integer debitNumber = unwrap(entry.getDebit());
            Integer creditNumber = unwrap(entry.getCredit());
            Float amount = unwrap(entry.getAmount());

            if (entry.getId() == null) {
                if (date == null || text == null || debitNumber == null || creditNumber == null || amount == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New booking requires date, text, debit, credit, and amount");
                }
                Account debitAccount = resolveAccount(project, debitNumber);
                Account creditAccount = resolveAccount(project, creditNumber);
                ch.bzz.entity.Booking created = new ch.bzz.entity.Booking(
                        null,
                        ++maxBookingNumber,
                        date,
                        text,
                        debitAccount,
                        creditAccount,
                        amount,
                        project
                );
                bookingRepository.save(created);
                log.debug("Created booking {} for project '{}'", created.getBookingNumber(), project.getProjectName());
            } else {
                ch.bzz.entity.Booking existing = bookingRepository.findByProjectAndBookingNumber(project, entry.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found: " + entry.getId()));

                if (date == null && text == null && debitNumber == null && creditNumber == null && amount == null) {
                    bookingRepository.delete(existing);
                    log.debug("Deleted booking {} for project '{}'", entry.getId(), project.getProjectName());
                } else {
                    if (date != null) {
                        existing.setDate(date);
                    }
                    if (text != null) {
                        existing.setText(text);
                    }
                    if (debitNumber != null) {
                        existing.setDebitAccount(resolveAccount(project, debitNumber));
                    }
                    if (creditNumber != null) {
                        existing.setCreditAccount(resolveAccount(project, creditNumber));
                    }
                    if (amount != null) {
                        existing.setAmount(amount);
                    }
                    bookingRepository.save(existing);
                    log.debug("Updated booking {} for project '{}'", entry.getId(), project.getProjectName());
                }
            }
        }

        return ResponseEntity.ok().build();
    }

    private Project getAuthenticatedProject() {
        String projectName = jwtUtil.resolveProjectName(request);
        return projectRepository.findById(projectName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private Account resolveAccount(Project project, Integer accountNumber) {
        return accountRepository.findByProjectAndAccountNumber(project, accountNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account not found: " + accountNumber));
    }

    private static <T> T unwrap(JsonNullable<T> value) {
        return (value != null && value.isPresent()) ? value.get() : null;
    }
}
