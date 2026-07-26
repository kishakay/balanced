package com.balanced.ledger.web.dto;

import com.balanced.ledger.domain.EntryStatus;
import com.balanced.ledger.domain.JournalEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JournalEntryResponse(
        UUID id,
        String idempotencyKey,
        String description,
        EntryStatus status,
        Instant createdAt,
        Instant voidedAt,
        UUID voidsEntryId,
        List<JournalLineResponse> lines
) {
    public static JournalEntryResponse from(JournalEntry entry) {
        return new JournalEntryResponse(
                entry.getId(),
                entry.getIdempotencyKey(),
                entry.getDescription(),
                entry.getStatus(),
                entry.getCreatedAt(),
                entry.getVoidedAt(),
                entry.getVoidsEntry() == null ? null : entry.getVoidsEntry().getId(),
                entry.getLines().stream().map(JournalLineResponse::from).toList()
        );
    }
}
