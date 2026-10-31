package com.vhcctweaks.ccvault;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.vhcctweaks.VHCCTweaks;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;

/**
 * Append-only ledger of all CCVault transactions, one JSON object per line.
 * The full record is vhcc_data/ccvault/ledger/transactions.jsonl, and each player involved in a
 * transaction also gets a copy in ledger/participants, so a lookup never has to read everyone's history.
 * A line a crash cut short is skipped when read.
 */
public class TransactionLedger {

    private static final Gson GSON = new Gson();
    private static Path ledgerFile;
    private static Path participantHistoryDir;

    public static void init(Path dataDir) {
        ledgerFile = dataDir.resolve("ccvault").resolve("ledger").resolve("transactions.jsonl");
        participantHistoryDir = ledgerFile.getParent().resolve("participants");
        try {
            Files.createDirectories(participantHistoryDir);
            endLastLine(ledgerFile);
            try (var files = Files.list(participantHistoryDir)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    endLastLine(file);
                }
            }
            rebuildParticipantIndexesIfNeeded();
        } catch (IOException e) {
            VHCCTweaks.LOGGER.error("CCVault: Failed to prepare ledger directory", e);
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
        appendEntry(entry, participants(fromUuid, toUuid, playerUuid, hostUuid));
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
        appendEntry(entry, participants(playerUuid));
    }

    /**
     * Look up a single transaction by ID. Returns a map suitable for Lua, or null if not found.
     * Only returns the entry if the given participantUuid was involved.
     */
    public static Map<String, Object> findTransaction(String txId, UUID participantUuid) {
        Path file = participantHistoryFile(participantUuid);
        if (file == null || !Files.exists(file)) file = ledgerFile;
        if (file == null || !Files.exists(file)) return null;

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.contains(txId)) continue;
                Map<?, ?> entry = parseLine(line);
                if (entry == null || !txId.equals(stringVal(entry, "txId"))) continue;

                // Security filter: participant must be involved
                if (!entryParticipants(entry).contains(participantUuid)) continue;

                return toLuaMap(entry);
            }
        } catch (IOException e) {
            VHCCTweaks.LOGGER.error("CCVault: Failed to read ledger for txId lookup", e);
        }
        return null;
    }

    /**
     * Get the last N transactions involving a given participant.
     * Returns a list of maps suitable for Lua tables.
     */
    public static List<Map<String, Object>> getHistory(UUID participantUuid, int limit) {
        if (limit <= 0) return Collections.emptyList();

        Path participantFile = participantHistoryFile(participantUuid);
        if (participantFile != null && Files.exists(participantFile)) {
            return readHistoryFile(participantFile, limit, null);
        }

        if (ledgerFile == null || !Files.exists(ledgerFile)) return Collections.emptyList();
        return readHistoryFile(ledgerFile, limit, participantUuid);
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

    private static Map<?, ?> parseLine(String line) {
        try {
            return GSON.fromJson(line, Map.class);
        } catch (JsonParseException e) {
            VHCCTweaks.LOGGER.warn("CCVault: Skipping unreadable ledger line");
            return null;
        }
    }

    private static Set<UUID> participants(UUID... uuids) {
        Set<UUID> participants = new LinkedHashSet<>();
        for (UUID uuid : uuids) {
            if (uuid != null) participants.add(uuid);
        }
        return participants;
    }

    private static Set<UUID> entryParticipants(Map<?, ?> entry) {
        return participants(uuidVal(entry, "from"), uuidVal(entry, "to"),
                uuidVal(entry, "playerUuid"), uuidVal(entry, "hostUuid"));
    }

    private static synchronized void appendEntry(Object entry, Set<UUID> participants) {
        if (ledgerFile == null) return;
        String line = GSON.toJson(entry) + "\n";
        try {
            Files.writeString(ledgerFile, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            VHCCTweaks.LOGGER.error("CCVault: Failed to write ledger entry", e);
        }
        for (UUID participant : participants) {
            appendParticipantLine(participant, line);
        }
    }

    private static void appendParticipantLine(UUID participantUuid, String line) {
        Path participantFile = participantHistoryFile(participantUuid);
        if (participantFile == null) return;

        try {
            Files.createDirectories(participantFile.getParent());
            Files.writeString(participantFile, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            VHCCTweaks.LOGGER.error("CCVault: Failed to write participant ledger for {}", participantUuid, e);
        }
    }

    private static Path participantHistoryFile(UUID participantUuid) {
        if (participantHistoryDir == null || participantUuid == null) return null;
        return participantHistoryDir.resolve(participantUuid + ".jsonl");
    }

    private static List<Map<String, Object>> readHistoryFile(Path file, int limit, UUID participantUuid) {
        LinkedList<Map<String, Object>> results = new LinkedList<>();

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Map<?, ?> entry = parseLine(line);
                if (entry == null) continue;
                if (participantUuid != null && !entryParticipants(entry).contains(participantUuid)) continue;

                results.addLast(toLuaMap(entry));
                if (results.size() > limit) {
                    results.removeFirst();
                }
            }
        } catch (IOException e) {
            VHCCTweaks.LOGGER.error("CCVault: Failed to read ledger history from {}", file.getFileName(), e);
        }

        return results;
    }

    /** A crash can leave a file ending mid-line. Without this the next entry would be glued onto that line and lost too. */
    private static void endLastLine(Path file) throws IOException {
        if (!Files.exists(file) || Files.size(file) == 0) return;
        try (RandomAccessFile access = new RandomAccessFile(file.toFile(), "rw")) {
            access.seek(access.length() - 1);
            if (access.read() != '\n') access.write('\n');
        }
    }

    private static void rebuildParticipantIndexesIfNeeded() throws IOException {
        if (ledgerFile == null || participantHistoryDir == null || !Files.exists(ledgerFile)) return;

        try (var files = Files.list(participantHistoryDir)) {
            if (files.findAny().isPresent()) {
                return;
            }
        }

        try (BufferedReader reader = Files.newBufferedReader(ledgerFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Map<?, ?> entry = parseLine(line);
                if (entry == null) continue;

                for (UUID participant : entryParticipants(entry)) {
                    appendParticipantLine(participant, line + "\n");
                }
            }
        }
    }

    private static UUID uuidVal(Map<?, ?> map, String key) {
        String value = stringVal(map, key);
        if (value == null || value.isBlank()) return null;

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
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
