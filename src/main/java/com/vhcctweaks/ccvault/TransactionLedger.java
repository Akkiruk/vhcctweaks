package com.vhcctweaks.ccvault;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vhcctweaks.VHCCTweaks;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;

/**
 * Append-only double-entry ledger for all CCVault transactions.
 * Each transfer logs both the debit and credit side.
 * Stored as one JSON object per line (JSONL) in vhcc_data/ccvault/ledger/transactions.jsonl
 */
public class TransactionLedger {

    private static final Gson GSON = new Gson();
    private static Path ledgerFile;

    public static void init(Path dataDir) {
        ledgerFile = dataDir.resolve("ccvault").resolve("ledger").resolve("transactions.jsonl");
        try {
            Files.createDirectories(ledgerFile.getParent());
        } catch (IOException e) {
            VHCCTweaks.LOGGER.error("CCVault: Failed to create ledger directory", e);
        }
    }

    /**
     * Log a completed transfer to the ledger.
     */
    public static void logTransfer(String txId, UUID fromUuid, UUID toUuid, long amount,
                                    String reason, int computerId, UUID playerUuid,
                                    UUID hostUuid) {
        LedgerEntry entry = new LedgerEntry(
                txId,
                fromUuid.toString(),
                toUuid.toString(),
                amount,
                reason,
                computerId,
                playerUuid.toString(),
                hostUuid != null ? hostUuid.toString() : null,
                Instant.now().toString()
        );
        appendEntry(entry);
    }

    /**
     * Log a failed/rejected transfer attempt.
     */
    public static void logRejection(String txId, UUID fromUuid, UUID toUuid, long amount,
                                     String reason, int computerId, UUID playerUuid,
                                     String rejectionReason) {
        RejectionEntry entry = RejectionEntry.create(
                txId,
                fromUuid != null ? fromUuid.toString() : null,
                toUuid != null ? toUuid.toString() : null,
                amount,
                reason,
                computerId,
                playerUuid != null ? playerUuid.toString() : null,
                rejectionReason,
                Instant.now().toString()
        );
        appendEntry(entry);
    }

    /**
     * Look up a single transaction by ID. Returns a map suitable for Lua, or null if not found.
     * Only returns the entry if the given participantUuid was involved (player or host).
     */
    public static Map<String, Object> findTransaction(String txId, UUID participantUuid) {
        if (ledgerFile == null || !Files.exists(ledgerFile)) return null;
        String participantStr = participantUuid.toString();

        try (BufferedReader reader = Files.newBufferedReader(ledgerFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.contains(txId)) continue;
                Map<?, ?> entry = GSON.fromJson(line, Map.class);
                if (entry == null) continue;

                String entryTxId = stringVal(entry, "txId");
                if (entryTxId == null) entryTxId = stringVal(entry, "type");
                if (entryTxId == null || !entryTxId.contains(txId)) continue;

                // Security filter: participant must be involved
                String playerUuid = stringVal(entry, "playerUuid");
                String hostUuid = stringVal(entry, "hostUuid");
                if (!participantStr.equals(playerUuid) && !participantStr.equals(hostUuid)) continue;

                return toLuaMap(entry);
            }
        } catch (IOException e) {
            VHCCTweaks.LOGGER.error("CCVault: Failed to read ledger for txId lookup", e);
        }
        return null;
    }

    /**
     * Get the last N transactions involving a given participant (player or host UUID).
     * Returns a list of maps suitable for Lua tables.
     */
    public static List<Map<String, Object>> getHistory(UUID participantUuid, int limit) {
        if (ledgerFile == null || !Files.exists(ledgerFile)) return Collections.emptyList();
        String participantStr = participantUuid.toString();

        // Read all matching lines, then return the last 'limit' ones
        LinkedList<Map<String, Object>> results = new LinkedList<>();
        try (BufferedReader reader = Files.newBufferedReader(ledgerFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.contains(participantStr)) continue;
                Map<?, ?> entry = GSON.fromJson(line, Map.class);
                if (entry == null) continue;

                String playerUuid = stringVal(entry, "playerUuid");
                String hostUuid = stringVal(entry, "hostUuid");
                if (!participantStr.equals(playerUuid) && !participantStr.equals(hostUuid)) continue;

                results.addLast(toLuaMap(entry));
                if (results.size() > limit) {
                    results.removeFirst();
                }
            }
        } catch (IOException e) {
            VHCCTweaks.LOGGER.error("CCVault: Failed to read ledger for history", e);
        }
        return results;
    }

    private static String stringVal(Map<?, ?> map, String key) {
        Object val = map.get(key);
        return val instanceof String s ? s : null;
    }

    private static Map<String, Object> toLuaMap(Map<?, ?> raw) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            if (e.getKey() instanceof String key) {
                result.put(key, e.getValue());
            }
        }
        return result;
    }

    private static synchronized void appendEntry(Object entry) {
        if (ledgerFile == null) return;
        try {
            String line = GSON.toJson(entry) + "\n";
            Files.writeString(ledgerFile, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            VHCCTweaks.LOGGER.error("CCVault: Failed to write ledger entry", e);
        }
    }

    // ---- Entry types ----

    private record LedgerEntry(
            String txId,
            String from,
            String to,
            long amount,
            String reason,
            int computerId,
            String playerUuid,
            String hostUuid,
            String timestamp
    ) {}

    private record RejectionEntry(
            String type,
            String from,
            String to,
            long amount,
            String reason,
            int computerId,
            String playerUuid,
            String rejectionReason,
            String timestamp
    ) {
        static RejectionEntry create(String txId, String from, String to, long amount, String reason,
                                      int computerId, String playerUuid, String rejectionReason, String timestamp) {
            return new RejectionEntry("REJECTED:" + txId, from, to, amount, reason,
                    computerId, playerUuid, rejectionReason, timestamp);
        }
    }
}
