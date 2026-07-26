package com.balanced.ledger.service;

import com.balanced.ledger.domain.Account;
import com.balanced.ledger.domain.AccountStatus;
import com.balanced.ledger.repository.AccountRepository;
import com.balanced.ledger.repository.LedgerLineRepository;
import com.balanced.ledger.web.dto.AccountResponse;
import com.balanced.ledger.web.dto.BalanceResponse;
import com.balanced.ledger.web.dto.CreateAccountRequest;
import com.balanced.ledger.web.dto.MoneyFormat;
import com.balanced.ledger.web.error.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final LedgerLineRepository ledgerLineRepository;

    public AccountService(AccountRepository accountRepository, LedgerLineRepository ledgerLineRepository) {
        this.accountRepository = accountRepository;
        this.ledgerLineRepository = ledgerLineRepository;
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        if (accountRepository.existsByCode(request.code())) {
            throw ApiException.conflict("ACCOUNT_CODE_EXISTS", "Account code already exists: " + request.code());
        }
        Account account = new Account(
                UUID.randomUUID(),
                request.code(),
                request.name(),
                request.accountType(),
                request.currency()
        );
        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list() {
        return accountRepository.findAllByOrderByCodeAsc().stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UUID id) {
        return AccountResponse.from(requireAccount(id));
    }

    @Transactional(readOnly = true)
    public BalanceResponse balance(UUID id) {
        Account account = requireAccount(id);
        LedgerLineRepository.DebitCreditTotals totals = ledgerLineRepository.sumPostedTotals(id);
        long debit = totals == null || totals.getDebitTotal() == null ? 0L : totals.getDebitTotal();
        long credit = totals == null || totals.getCreditTotal() == null ? 0L : totals.getCreditTotal();
        long balanceCents = account.getAccountType().isDebitNormal()
                ? debit - credit
                : credit - debit;
        return new BalanceResponse(
                account.getId(),
                account.getCode(),
                account.getCurrency(),
                balanceCents,
                MoneyFormat.format(balanceCents),
                debit,
                credit
        );
    }

    @Transactional
    public AccountResponse close(UUID id) {
        Account account = requireAccount(id);
        if (account.getStatus() == AccountStatus.CLOSED) {
            return AccountResponse.from(account);
        }
        BalanceResponse balance = balance(id);
        if (balance.balanceCents() != 0) {
            throw ApiException.unprocessable(
                    "ACCOUNT_NOT_ZERO",
                    "Cannot close account with non-zero balance: " + balance.balance()
            );
        }
        account.close();
        return AccountResponse.from(account);
    }

    Account requireAccount(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Account not found: " + id));
    }

    Account requireActiveAccount(UUID id) {
        Account account = requireAccount(id);
        if (!account.isActive()) {
            throw ApiException.unprocessable("ACCOUNT_CLOSED", "Account is closed: " + account.getCode());
        }
        return account;
    }
}
