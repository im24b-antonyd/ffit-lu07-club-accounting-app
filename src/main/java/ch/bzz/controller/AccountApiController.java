package ch.bzz.controller;

import ch.bzz.entity.Project;
import ch.bzz.generated.api.AccountApi;
import ch.bzz.generated.model.Account;
import ch.bzz.generated.model.AccountUpdate;
import ch.bzz.generated.model.UpdateAccountsRequest;
import ch.bzz.repository.AccountRepository;
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

import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
public class AccountApiController implements AccountApi {

    private final AccountRepository accountRepository;
    private final ProjectRepository projectRepository;
    private final JwtUtil jwtUtil;
    private final HttpServletRequest request;

    public AccountApiController(
            AccountRepository accountRepository,
            ProjectRepository projectRepository,
            JwtUtil jwtUtil,
            HttpServletRequest request
    ) {
        this.accountRepository = accountRepository;
        this.projectRepository = projectRepository;
        this.jwtUtil = jwtUtil;
        this.request = request;
    }

    @Override
    public ResponseEntity<List<Account>> getAccounts() {
        Project project = getAuthenticatedProject();
        List<Account> accounts = accountRepository.findByProjectOrderByAccountNumberAsc(project)
                .stream()
                .map(entity -> new Account(entity.getAccountNumber(), entity.getName()))
                .toList();
        log.debug("Returning {} accounts for project '{}'", accounts.size(), project.getProjectName());
        return ResponseEntity.ok(accounts);
    }

    @Override
    @Transactional
    public ResponseEntity<Void> updateAccounts(UpdateAccountsRequest updateAccountsRequest) {
        Project project = getAuthenticatedProject();
        List<AccountUpdate> updates = updateAccountsRequest.getAccounts();
        if (updates == null) {
            return ResponseEntity.ok().build();
        }

        for (AccountUpdate update : updates) {
            Integer number = update.getNumber();
            JsonNullable<String> nameNullable = update.getName();
            String newName = (nameNullable != null && nameNullable.isPresent()) ? nameNullable.get() : null;

            Optional<ch.bzz.entity.Account> existingOpt = accountRepository.findByProjectAndAccountNumber(project, number);
            if (newName == null) {
                existingOpt.ifPresent(existing -> {
                    accountRepository.delete(existing);
                    log.debug("Deleted account {} for project '{}'", number, project.getProjectName());
                });
            } else if (existingOpt.isPresent()) {
                ch.bzz.entity.Account existing = existingOpt.get();
                existing.setName(newName);
                accountRepository.save(existing);
                log.debug("Updated account {} for project '{}'", number, project.getProjectName());
            } else {
                ch.bzz.entity.Account created = new ch.bzz.entity.Account(null, number, newName, project);
                accountRepository.save(created);
                log.debug("Created account {} for project '{}'", number, project.getProjectName());
            }
        }

        return ResponseEntity.ok().build();
    }

    private Project getAuthenticatedProject() {
        String projectName = jwtUtil.resolveProjectName(request);
        return projectRepository.findById(projectName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }
}
