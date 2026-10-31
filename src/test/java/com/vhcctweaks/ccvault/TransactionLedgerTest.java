package com.vhcctweaks.ccvault;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionLedgerTest {

    @TempDir
    Path dataDir;

    private final UUID from = UUID.randomUUID();
    private final UUID to = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();
    private final UUID host = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    @BeforeEach
    void init() {
        TransactionLedger.init(dataDir);
    }

    private Path ledgerFile() {
        return dataDir.resolve("ccvault/ledger/transactions.jsonl");
    }

    private Path participantFile(UUID uuid) {
        return dataDir.resolve("ccvault/ledger/participants/" + uuid + ".jsonl");
    }

    private void log(String txId) {
        TransactionLedger.logTransfer(txId, from, to, 10, "test", 1, player, host);
    }

    @Test
    void everyoneInvolvedCanFindATransaction() {
        log("tx1");
        for (UUID involved : List.of(from, to, player, host)) {
            assertNotNull(TransactionLedger.findTransaction("tx1", involved));
            assertEquals(1, TransactionLedger.getHistory(involved, 10).size());
        }
    }

    @Test
    void strangersCannotFindATransaction() {
        log("tx1");
        assertNull(TransactionLedger.findTransaction("tx1", stranger));
        assertTrue(TransactionLedger.getHistory(stranger, 10).isEmpty());
    }

    @Test
    void historyKeepsTheNewestEntries() {
        log("tx1");
        log("tx2");
        log("tx3");
        List<Map<String, Object>> history = TransactionLedger.getHistory(player, 2);
        assertEquals("tx2", history.get(0).get("txId"));
        assertEquals("tx3", history.get(1).get("txId"));
    }

    @Test
    void aLineCutShortIsSkipped() throws IOException {
        log("tx1");
        Files.writeString(participantFile(player), "{\"txId\":\"torn", StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        assertEquals(1, TransactionLedger.getHistory(player, 10).size());
        assertNotNull(TransactionLedger.findTransaction("tx1", player));
    }

    @Test
    void anEntryAfterACrashIsNotLostToTheTornLine() throws IOException {
        log("tx1");
        Files.writeString(ledgerFile(), "{\"txId\":\"torn", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        Files.writeString(participantFile(player), "{\"txId\":\"torn", StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        TransactionLedger.init(dataDir);
        log("tx2");

        assertNotNull(TransactionLedger.findTransaction("tx2", player));
        assertEquals(2, TransactionLedger.getHistory(player, 10).size());
    }

    @Test
    void participantFilesRebuildFromTheLedgerPastATornLine() throws IOException {
        log("tx1");
        Files.writeString(ledgerFile(), "{\"txId\":\"torn\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        for (UUID involved : List.of(from, to, player, host)) {
            Files.delete(participantFile(involved));
        }

        TransactionLedger.init(dataDir);

        assertTrue(Files.exists(participantFile(to)));
        assertNotNull(TransactionLedger.findTransaction("tx1", to));
    }
}
