package com.balanced.ledger.config;

import com.balanced.ledger.domain.AccountType;
import com.balanced.ledger.domain.Direction;
import com.balanced.ledger.repository.AccountRepository;
import com.balanced.ledger.service.AccountService;
import com.balanced.ledger.service.LedgerService;
import com.balanced.ledger.web.dto.AccountResponse;
import com.balanced.ledger.web.dto.CreateAccountRequest;
import com.balanced.ledger.web.dto.CreateJournalEntryRequest;
import com.balanced.ledger.web.dto.TransferRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final boolean seedDemoData;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final LedgerService ledgerService;

    public DemoDataSeeder(
            @Value("${balanced.seed-demo-data:true}") boolean seedDemoData,
            AccountRepository accountRepository,
            AccountService accountService,
            LedgerService ledgerService
    ) {
        this.seedDemoData = seedDemoData;
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.ledgerService = ledgerService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedDemoData || accountRepository.count() > 0) {
            return;
        }

        log.info("Seeding demo ledger data");

        AccountResponse opening = accountService.create(new CreateAccountRequest(
                "EQUITY-OPENING", "Opening Balances", AccountType.EQUITY, "USD"));
        AccountResponse operating = accountService.create(new CreateAccountRequest(
                "ASSET-OPERATING", "Operating Cash", AccountType.ASSET, "USD"));
        AccountResponse reserves = accountService.create(new CreateAccountRequest(
                "ASSET-RESERVES", "Reserve Cash", AccountType.ASSET, "USD"));
        accountService.create(new CreateAccountRequest(
                "LIAB-PAYABLE", "Accounts Payable", AccountType.LIABILITY, "USD"));

        ledgerService.createJournalEntry(
                "seed-open-operating",
                new CreateJournalEntryRequest(
                        "Seed opening balance for operating cash",
                        List.of(
                                new CreateJournalEntryRequest.Line(
                                        operating.id(), Direction.DEBIT, 250_000L, "USD"),
                                new CreateJournalEntryRequest.Line(
                                        opening.id(), Direction.CREDIT, 250_000L, "USD")
                        )
                )
        );

        ledgerService.createJournalEntry(
                "seed-open-reserves",
                new CreateJournalEntryRequest(
                        "Seed opening balance for reserve cash",
                        List.of(
                                new CreateJournalEntryRequest.Line(
                                        reserves.id(), Direction.DEBIT, 75_000L, "USD"),
                                new CreateJournalEntryRequest.Line(
                                        opening.id(), Direction.CREDIT, 75_000L, "USD")
                        )
                )
        );

        ledgerService.transfer(
                "seed-transfer-ops-to-reserves",
                new TransferRequest(
                        operating.id(),
                        reserves.id(),
                        25_000L,
                        "USD",
                        "Seed transfer: operating → reserves"
                )
        );

        log.info("Demo seed complete");
    }
}
