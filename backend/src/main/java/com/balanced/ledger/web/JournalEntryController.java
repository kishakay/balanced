package com.balanced.ledger.web;

import com.balanced.ledger.service.LedgerService;
import com.balanced.ledger.web.dto.CreateJournalEntryRequest;
import com.balanced.ledger.web.dto.JournalEntryResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/journal-entries")
public class JournalEntryController {

    private final LedgerService ledgerService;

    public JournalEntryController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JournalEntryResponse create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateJournalEntryRequest request
    ) {
        return ledgerService.createJournalEntry(idempotencyKey, request);
    }

    @GetMapping
    public List<JournalEntryResponse> list() {
        return ledgerService.list();
    }

    @GetMapping("/{id}")
    public JournalEntryResponse get(@PathVariable UUID id) {
        return ledgerService.get(id);
    }

    @PostMapping("/{id}/void")
    @ResponseStatus(HttpStatus.CREATED)
    public JournalEntryResponse voidEntry(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return ledgerService.voidEntry(id, idempotencyKey);
    }
}
