package com.balanced.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LedgerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE idempotency_records, ledger_lines, journal_entries, accounts CASCADE");
    }

    @Test
    void createsAccountsComputesBalanceAndTransfersIdempotently() throws Exception {
        UUID equity = createAccount("EQUITY-OPENING", "Opening", "EQUITY");
        UUID operating = createAccount("ASSET-OPERATING", "Operating", "ASSET");
        UUID reserves = createAccount("ASSET-RESERVES", "Reserves", "ASSET");

        fund(operating, equity, 100_00, "fund-operating");

        mockMvc.perform(get("/api/v1/accounts/{id}/balance", operating))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceCents").value(100_00));

        String transferBody = """
                {
                  "fromAccountId": "%s",
                  "toAccountId": "%s",
                  "amountCents": 4000,
                  "currency": "USD",
                  "description": "Move to reserves"
                }
                """.formatted(operating, reserves);

        MvcResult first = mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "xfer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andReturn();

        String entryId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        MvcResult replay = mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "xfer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(objectMapper.readTree(replay.getResponse().getContentAsString()).get("id").asText())
                .isEqualTo(entryId);

        mockMvc.perform(get("/api/v1/accounts/{id}/balance", operating))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceCents").value(60_00));

        mockMvc.perform(get("/api/v1/accounts/{id}/balance", reserves))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceCents").value(40_00));

        Long entryCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM journal_entries", Long.class);
        assertThat(entryCount).isEqualTo(2);
    }

    @Test
    void rejectsIdempotencyKeyReuseWithDifferentPayload() throws Exception {
        UUID equity = createAccount("EQUITY-OPENING", "Opening", "EQUITY");
        UUID operating = createAccount("ASSET-OPERATING", "Operating", "ASSET");
        UUID reserves = createAccount("ASSET-RESERVES", "Reserves", "ASSET");
        fund(operating, equity, 100_00, "fund-operating");

        String bodyA = transferJson(operating, reserves, 10_00, "A");
        String bodyB = transferJson(operating, reserves, 20_00, "B");

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "same-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyA))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "same-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyB))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void rejectsUnbalancedJournalEntryAndInsufficientFunds() throws Exception {
        UUID equity = createAccount("EQUITY-OPENING", "Opening", "EQUITY");
        UUID operating = createAccount("ASSET-OPERATING", "Operating", "ASSET");
        UUID reserves = createAccount("ASSET-RESERVES", "Reserves", "ASSET");
        fund(operating, equity, 50_00, "fund-operating");

        String unbalanced = """
                {
                  "description": "bad",
                  "lines": [
                    {"accountId": "%s", "direction": "DEBIT", "amountCents": 100, "currency": "USD"},
                    {"accountId": "%s", "direction": "CREDIT", "amountCents": 50, "currency": "USD"}
                  ]
                }
                """.formatted(operating, equity);

        mockMvc.perform(post("/api/v1/journal-entries")
                        .header("Idempotency-Key", "unbalanced")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unbalanced))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNBALANCED"));

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "too-much")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(operating, reserves, 80_00, "too much")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void voidsEntryWithReversingPairAndKeepsAuditTrail() throws Exception {
        UUID equity = createAccount("EQUITY-OPENING", "Opening", "EQUITY");
        UUID operating = createAccount("ASSET-OPERATING", "Operating", "ASSET");
        fund(operating, equity, 100_00, "fund-operating");

        MvcResult transfer = mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "to-void")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fromAccountId": "%s",
                                  "toAccountId": "%s",
                                  "amountCents": 2500,
                                  "currency": "USD",
                                  "description": "Will void"
                                }
                                """.formatted(operating, createAccount("ASSET-HOLDING", "Holding", "ASSET"))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode entry = objectMapper.readTree(transfer.getResponse().getContentAsString());
        String entryId = entry.get("id").asText();

        mockMvc.perform(post("/api/v1/journal-entries/{id}/void", entryId)
                        .header("Idempotency-Key", "void-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.voidsEntryId").value(entryId))
                .andExpect(jsonPath("$.lines.length()").value(2));

        mockMvc.perform(get("/api/v1/journal-entries/{id}", entryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED"));

        mockMvc.perform(get("/api/v1/accounts/{id}/balance", operating))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceCents").value(100_00));

        mockMvc.perform(get("/api/v1/accounts/{id}/transactions", operating))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    private UUID createAccount(String code, String name, String type) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "%s",
                                  "accountType": "%s",
                                  "currency": "USD"
                                }
                                """.formatted(code, name, type)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private void fund(UUID asset, UUID equity, long cents, String key) throws Exception {
        mockMvc.perform(post("/api/v1/journal-entries")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Funding",
                                  "lines": [
                                    {"accountId": "%s", "direction": "DEBIT", "amountCents": %d, "currency": "USD"},
                                    {"accountId": "%s", "direction": "CREDIT", "amountCents": %d, "currency": "USD"}
                                  ]
                                }
                                """.formatted(asset, cents, equity, cents)))
                .andExpect(status().isCreated());
    }

    private static String transferJson(UUID from, UUID to, long cents, String description) {
        return """
                {
                  "fromAccountId": "%s",
                  "toAccountId": "%s",
                  "amountCents": %d,
                  "currency": "USD",
                  "description": "%s"
                }
                """.formatted(from, to, cents, description);
    }
}
