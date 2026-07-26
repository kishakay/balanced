package com.balanced.ledger.repository;

import com.balanced.ledger.domain.JournalEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    Optional<JournalEntry> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            SELECT DISTINCT je FROM JournalEntry je
            JOIN FETCH je.lines l
            JOIN FETCH l.account
            WHERE je.id = :id
            """)
    Optional<JournalEntry> findDetailedById(@Param("id") UUID id);

    @Query("""
            SELECT DISTINCT je FROM JournalEntry je
            JOIN FETCH je.lines l
            JOIN FETCH l.account
            ORDER BY je.createdAt DESC
            """)
    List<JournalEntry> findAllDetailed();

    @Query("""
            SELECT DISTINCT je FROM JournalEntry je
            JOIN FETCH je.lines l
            JOIN FETCH l.account a
            WHERE a.id = :accountId
            ORDER BY je.createdAt DESC
            """)
    List<JournalEntry> findDetailedByAccountId(@Param("accountId") UUID accountId);
}
