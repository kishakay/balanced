package com.balanced.ledger.repository;

import com.balanced.ledger.domain.Account;
import com.balanced.ledger.domain.AccountStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByCode(String code);

    boolean existsByCode(String code);

    List<Account> findAllByStatusOrderByCodeAsc(AccountStatus status);

    List<Account> findAllByOrderByCodeAsc();
}
