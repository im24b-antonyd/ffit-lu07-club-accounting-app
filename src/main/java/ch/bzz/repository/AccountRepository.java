package ch.bzz.repository;

import ch.bzz.entity.Account;
import ch.bzz.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByProject(Project project);

    List<Account> findByProjectOrderByAccountNumberAsc(Project project);

    Optional<Account> findByProjectAndAccountNumber(Project project, Integer accountNumber);
}
