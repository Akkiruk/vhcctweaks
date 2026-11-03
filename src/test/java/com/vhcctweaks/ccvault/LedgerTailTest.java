package com.vhcctweaks.ccvault;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Reading the ledger from its end must see exactly the lines a forward read sees, in reverse. */
class LedgerTailTest {

    @TempDir
    Path dir;

    private List<String> backwards(Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        TransactionLedger.forEachLineFromEnd(file, line -> { lines.add(line); return true; });
        Collections.reverse(lines);
        // The empty text after a final newline is visited too; the ledger readers skip it as an unparsable line
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1);
        return lines;
    }

    private static List<String> forwards(String text) {
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        if (text.endsWith("\n")) lines.remove(lines.size() - 1);
        return lines;
    }

    @Test
    void longLinesAcrossBlockBoundariesAndUnicodeComeBackWhole() throws IOException {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            text.append("{\"txId\":\"").append(i).append("\",\"reason\":\"").append("é€😀".repeat(i % 40)).append("x".repeat(i * 37 % 9000)).append("\"}\n");
        }
        Path file = dir.resolve("a.jsonl");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        assertEquals(forwards(text.toString()), backwards(file));
    }

    @Test
    void aFileWithoutAFinalNewlineKeepsItsLastLine() throws IOException {
        Path file = dir.resolve("b.jsonl");
        Files.writeString(file, "one\ntwo\nthree", StandardCharsets.UTF_8);
        assertEquals(List.of("one", "two", "three"), backwards(file));
    }

    @Test
    void stoppingEarlyReadsOnlyTheNewest() throws IOException {
        Path file = dir.resolve("c.jsonl");
        Files.writeString(file, "1\n2\n3\n4\n", StandardCharsets.UTF_8);
        List<String> seen = new ArrayList<>();
        TransactionLedger.forEachLineFromEnd(file, line -> { if (!line.isEmpty()) seen.add(line); return seen.size() < 2; });
        assertEquals(List.of("4", "3"), seen);
    }

    @Test
    void anEmptyFileVisitsNothing() throws IOException {
        Path file = dir.resolve("d.jsonl");
        Files.writeString(file, "");
        assertEquals(List.of(), backwards(file));
    }
}
