package com.balanced.ledger.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "journal_entries")
public class JournalEntry {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(nullable = false, length = 512)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EntryStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voids_entry_id")
    private JournalEntry voidsEntry;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LedgerLine> lines = new ArrayList<>();

    protected JournalEntry() {
    }

    public JournalEntry(UUID id, String idempotencyKey, String description) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.description = description;
        this.status = EntryStatus.POSTED;
        this.createdAt = Instant.now();
    }

    public void addLine(LedgerLine line) {
        lines.add(line);
        line.setJournalEntry(this);
    }

    public void markVoided() {
        this.status = EntryStatus.VOIDED;
        this.voidedAt = Instant.now();
    }

    public void setVoidsEntry(JournalEntry voidsEntry) {
        this.voidsEntry = voidsEntry;
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getDescription() {
        return description;
    }

    public EntryStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public JournalEntry getVoidsEntry() {
        return voidsEntry;
    }

    public List<LedgerLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public boolean isPosted() {
        return status == EntryStatus.POSTED;
    }
}
