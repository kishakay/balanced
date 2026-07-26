package com.balanced.ledger.repository;

import com.balanced.ledger.domain.LedgerLine;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerLineRepository extends JpaRepository<LedgerLine, UUID> {

    /**
     * Balance includes every posted line, including entries later marked VOIDED.
     * Voids are paired with reversing entries so the net effect is zero while the audit trail remains intact.
     */
    @Query(value = """
            SELECT
              COALESCE(SUM(CASE WHEN ll.direction = 'DEBIT'  THEN ll.amount_cents ELSE 0 END), 0) AS debitTotal,
              COALESCE(SUM(CASE WHEN ll.direction = 'CREDIT' THEN ll.amount_cents ELSE 0 END), 0) AS creditTotal
            FROM ledger_lines ll
            WHERE ll.account_id = :accountId
            """, nativeQuery = true)
    DebitCreditTotals sumPostedTotals(@Param("accountId") UUID accountId);

    interface DebitCreditTotals {
        Long getDebitTotal();

        Long getCreditTotal();
    }
}
