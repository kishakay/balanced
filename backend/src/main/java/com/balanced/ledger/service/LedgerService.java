package com.balanced.ledger.service;

import com.balanced.ledger.domain.Account;
import com.balanced.ledger.domain.Direction;
import com.balanced.ledger.domain.IdempotencyRecord;
import com.balanced.ledger.domain.JournalEntry;
import com.balanced.ledger.domain.LedgerLine;
import com.balanced.ledger.repository.IdempotencyRecordRepository;
import com.balanced.ledger.repository.JournalEntryRepository;
import com.balanced.ledger.web.dto.CreateJournalEntryRequest;
import com.balanced.ledger.web.dto.JournalEntryResponse;
import com.balanced.ledger.web.dto.TransferRequest;
import com.balanced.ledger.web.error.ApiException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

    private final JournalEntryRepository journalEntryRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final AccountService accountService;

    public LedgerService(
            JournalEntryRepository journalEntryRepository,
            IdempotencyRecordRepository idempotencyRecordRepository,
            AccountService accountService
    ) {
        this.journalEntryRepository = journalEntryRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.accountService = accountService;
    }

    @Transactional
    public JournalEntryResponse transfer(String idempotencyKey, TransferRequest request) {
        validateIdempotencyKey(idempotencyKey);
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw ApiException.badRequest("SAME_ACCOUNT", "fromAccountId and toAccountId must differ");
        }

        String requestHash = MoneyHasher.sha256(String.join("|",
                "TRANSFER",
                request.fromAccountId().toString(),
                request.toAccountId().toString(),
                Long.toString(request.amountCents()),
                request.currency(),
                request.description()
        ));

        Optional<JournalEntryResponse> replay = replayIfPresent(idempotencyKey, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        Account from = accountService.requireActiveAccount(request.fromAccountId());
        Account to = accountService.requireActiveAccount(request.toAccountId());

        if (!from.getCurrency().equals(request.currency()) || !to.getCurrency().equals(request.currency())) {
            throw ApiException.unprocessable(
                    "CURRENCY_MISMATCH",
                    "Transfer currency must match both account currencies ("
                            + from.getCurrency() + ", " + to.getCurrency() + ")"
            );
        }

        if (from.getAccountType() != to.getAccountType()) {
            throw ApiException.unprocessable(
                    "ACCOUNT_TYPE_MISMATCH",
                    "Transfers require matching account types; use journal entries for cross-type postings"
            );
        }

        long available = accountService.balance(from.getId()).balanceCents();
        if (available < request.amountCents()) {
            throw ApiException.unprocessable(
                    "INSUFFICIENT_FUNDS",
                    "Account " + from.getCode() + " has insufficient funds (available "
                            + available + " cents)"
            );
        }

        // Same-type move: decrease source, increase destination (balanced debit + credit).
        Direction fromDirection = from.getAccountType().isDebitNormal() ? Direction.CREDIT : Direction.DEBIT;
        Direction toDirection = fromDirection.opposite();

        CreateJournalEntryRequest journalRequest = new CreateJournalEntryRequest(
                request.description(),
                List.of(
                        new CreateJournalEntryRequest.Line(
                                from.getId(), fromDirection, request.amountCents(), request.currency()),
                        new CreateJournalEntryRequest.Line(
                                to.getId(), toDirection, request.amountCents(), request.currency())
                )
        );

        return postEntry(idempotencyKey, requestHash, journalRequest);
    }

    @Transactional
    public JournalEntryResponse createJournalEntry(String idempotencyKey, CreateJournalEntryRequest request) {
        validateIdempotencyKey(idempotencyKey);
        String requestHash = MoneyHasher.sha256("JOURNAL|" + canonicalJournalPayload(request));
        Optional<JournalEntryResponse> replay = replayIfPresent(idempotencyKey, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }
        return postEntry(idempotencyKey, requestHash, request);
    }

    @Transactional
    public JournalEntryResponse voidEntry(UUID entryId, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        JournalEntry original = journalEntryRepository.findDetailedById(entryId)
                .orElseThrow(() -> ApiException.notFound("Journal entry not found: " + entryId));

        if (!original.isPosted()) {
            throw ApiException.unprocessable("ALREADY_VOIDED", "Journal entry is already voided");
        }

        String requestHash = MoneyHasher.sha256("VOID|" + entryId);
        Optional<JournalEntryResponse> replay = replayIfPresent(idempotencyKey, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        List<CreateJournalEntryRequest.Line> reversingLines = original.getLines().stream()
                .map(line -> new CreateJournalEntryRequest.Line(
                        line.getAccount().getId(),
                        line.getDirection().opposite(),
                        line.getAmountCents(),
                        line.getCurrency()
                ))
                .toList();

        CreateJournalEntryRequest reversingRequest = new CreateJournalEntryRequest(
                "Void of " + original.getId() + ": " + original.getDescription(),
                reversingLines
        );

        JournalEntryResponse reversing = postEntry(idempotencyKey, requestHash, reversingRequest, original);
        original.markVoided();
        journalEntryRepository.save(original);
        return reversing;
    }

    @Transactional(readOnly = true)
    public JournalEntryResponse get(UUID id) {
        return JournalEntryResponse.from(journalEntryRepository.findDetailedById(id)
                .orElseThrow(() -> ApiException.notFound("Journal entry not found: " + id)));
    }

    @Transactional(readOnly = true)
    public List<JournalEntryResponse> list() {
        return journalEntryRepository.findAllDetailed().stream()
                .map(JournalEntryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JournalEntryResponse> listForAccount(UUID accountId) {
        accountService.requireAccount(accountId);
        return journalEntryRepository.findDetailedByAccountId(accountId).stream()
                .map(JournalEntryResponse::from)
                .toList();
    }

    private JournalEntryResponse postEntry(
            String idempotencyKey,
            String requestHash,
            CreateJournalEntryRequest request
    ) {
        return postEntry(idempotencyKey, requestHash, request, null);
    }

    private JournalEntryResponse postEntry(
            String idempotencyKey,
            String requestHash,
            CreateJournalEntryRequest request,
            JournalEntry voidsEntry
    ) {
        validateBalanced(request);

        JournalEntry entry = new JournalEntry(UUID.randomUUID(), idempotencyKey, request.description());
        if (voidsEntry != null) {
            entry.setVoidsEntry(voidsEntry);
        }

        String currency = null;
        for (CreateJournalEntryRequest.Line line : request.lines()) {
            Account account = accountService.requireActiveAccount(line.accountId());
            if (!account.getCurrency().equals(line.currency())) {
                throw ApiException.unprocessable(
                        "CURRENCY_MISMATCH",
                        "Line currency " + line.currency() + " does not match account "
                                + account.getCode() + " (" + account.getCurrency() + ")"
                );
            }
            if (currency == null) {
                currency = line.currency();
            } else if (!currency.equals(line.currency())) {
                throw ApiException.unprocessable(
                        "MULTI_CURRENCY",
                        "All lines in a journal entry must share the same currency"
                );
            }
            entry.addLine(new LedgerLine(
                    UUID.randomUUID(),
                    account,
                    line.direction(),
                    line.amountCents(),
                    line.currency()
            ));
        }

        try {
            JournalEntry saved = journalEntryRepository.saveAndFlush(entry);
            idempotencyRecordRepository.saveAndFlush(new IdempotencyRecord(idempotencyKey, requestHash, saved));
            return JournalEntryResponse.from(
                    journalEntryRepository.findDetailedById(saved.getId()).orElse(saved)
            );
        } catch (DataIntegrityViolationException ex) {
            Optional<JournalEntryResponse> replay = replayIfPresent(idempotencyKey, requestHash);
            if (replay.isPresent()) {
                return replay.get();
            }
            throw ApiException.conflict("IDEMPOTENCY_CONFLICT",
                    "Idempotency key already used with a different request payload");
        }
    }

    private Optional<JournalEntryResponse> replayIfPresent(String idempotencyKey, String requestHash) {
        return idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey)
                .map(record -> {
                    if (!record.getRequestHash().equals(requestHash)) {
                        throw ApiException.conflict(
                                "IDEMPOTENCY_CONFLICT",
                                "Idempotency key already used with a different request payload"
                        );
                    }
                    return JournalEntryResponse.from(
                            journalEntryRepository.findDetailedById(record.getJournalEntry().getId())
                                    .orElseThrow(() -> ApiException.notFound("Journal entry missing for idempotency key"))
                    );
                });
    }

    private static void validateBalanced(CreateJournalEntryRequest request) {
        if (request.lines().size() < 2) {
            throw ApiException.badRequest("UNBALANCED", "Journal entry requires at least two lines");
        }
        long debit = 0;
        long credit = 0;
        Set<String> seen = new HashSet<>();
        for (CreateJournalEntryRequest.Line line : request.lines()) {
            String key = line.accountId() + ":" + line.direction();
            if (!seen.add(key)) {
                throw ApiException.badRequest(
                        "DUPLICATE_LINE",
                        "Duplicate account/direction line is not allowed in a single entry"
                );
            }
            if (line.direction() == Direction.DEBIT) {
                debit += line.amountCents();
            } else {
                credit += line.amountCents();
            }
        }
        if (debit != credit) {
            throw ApiException.unprocessable(
                    "UNBALANCED",
                    "Debits (" + debit + ") must equal credits (" + credit + ")"
            );
        }
    }

    private static void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ApiException.badRequest("MISSING_IDEMPOTENCY_KEY", "Idempotency-Key header is required");
        }
        if (idempotencyKey.length() > 128) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be <= 128 characters");
        }
    }

    private static String canonicalJournalPayload(CreateJournalEntryRequest request) {
        StringBuilder builder = new StringBuilder(request.description()).append('|');
        request.lines().stream()
                .sorted((a, b) -> {
                    int byAccount = a.accountId().compareTo(b.accountId());
                    if (byAccount != 0) {
                        return byAccount;
                    }
                    return a.direction().compareTo(b.direction());
                })
                .forEach(line -> builder
                        .append(line.accountId()).append(':')
                        .append(line.direction().name()).append(':')
                        .append(line.amountCents()).append(':')
                        .append(line.currency().toUpperCase(Locale.ROOT)).append(';'));
        return builder.toString();
    }
}
