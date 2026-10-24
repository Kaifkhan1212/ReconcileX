import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Single-file generator for 50,000-row synthetic reconciliation datasets.
 * Run directly with: java scripts/generate-data/Generate.java
 */
public class Generate {

    private static final long SEED = 42L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 3, 1);

    public static void main(String[] args) throws IOException {
        System.out.println("Starting synthetic data generation (50,000 internal rows & 50,000 bank rows)...");
        Random rng = new Random(SEED);

        Path outputDir = Paths.get("scripts", "generate-data");
        Files.createDirectories(outputDir);

        Path internalPath = outputDir.resolve("internal_50k.csv");
        Path bankPath = outputDir.resolve("bank_statement_50k.csv");
        Path expectedPath = outputDir.resolve("expected_counts.json");

        Map<String, Long> expectedCounts = new LinkedHashMap<>();

        try (BufferedWriter internalWriter = new BufferedWriter(new FileWriter(internalPath.toFile()));
             BufferedWriter bankWriter = new BufferedWriter(new FileWriter(bankPath.toFile()))) {

            // CSV Headers
            internalWriter.write("transaction_id,payment_reference,transaction_date,description,amount,currency,payment_status,settlement_reference\n");
            bankWriter.write("transaction_date,bank_reference,payment_reference,settlement_reference,description,credit_amount,debit_amount,currency\n");

            long txnSeq = 1;
            long bnkSeq = 1;

            // 1. SCN-01: EXACT_MATCH (30,000 pairs = 30k internal, 30k bank)
            for (int i = 0; i < 30000; i++) {
                String ref = "PAY-EXACT-" + i;
                String stl = "STL-EXACT-" + i;
                String amt = String.format(Locale.ROOT, "%.2f", 100.00 + (i % 500) + 0.50);
                LocalDate date = BASE_DATE.plusDays(i % 25);

                internalWriter.write(String.format(Locale.ROOT, "TXN%07d,%s,%s,Checkout Order #%d,%s,INR,SUCCESS,%s\n",
                        txnSeq++, ref, date, i, amt, stl));
                bankWriter.write(String.format(Locale.ROOT, "%s,BNK%07d,%s,%s,Settlement credit order %d,%s,0.00,INR\n",
                        date, bnkSeq++, ref, stl, i, amt));
            }
            expectedCounts.put("EXACT_MATCH", 30000L);

            // 2. SCN-40: INTERNAL_DUPLICATE (1,000 groups: 2 internal rows, 1 bank row = 2,000 internal, 1,000 bank)
            // Primary matches EXACT_MATCH (Order 100), duplicate matches INTERNAL_DUPLICATE_DETECTED (Order 50)
            for (int i = 0; i < 1000; i++) {
                String ref = "PAY-INTDUP-" + i;
                String stl = "STL-INTDUP-" + i;
                String amt = String.format(Locale.ROOT, "%.2f", 120.00 + (i % 40));
                LocalDate date = BASE_DATE.plusDays(i % 20);

                // Lower transaction_id is primary (stays in pool for ExactMatchRule)
                internalWriter.write(String.format(Locale.ROOT, "TXN%07d,%s,%s,Original primary payment,%s,INR,SUCCESS,%s\n",
                        txnSeq++, ref, date, amt, stl));
                // Higher transaction_id is consumed by InternalDuplicateRule
                internalWriter.write(String.format(Locale.ROOT, "TXN%07d,%s,%s,Duplicate payment submission,%s,INR,SUCCESS,%s\n",
                        txnSeq++, ref, date, amt, stl));

                // Bank row matches primary
                bankWriter.write(String.format(Locale.ROOT, "%s,BNK%07d,%s,%s,Payout credit,%s,0.00,INR\n",
                        date, bnkSeq++, ref, stl, amt));
            }
            expectedCounts.put("INTERNAL_DUPLICATE_DETECTED", 1000L);
            expectedCounts.put("EXACT_MATCH", expectedCounts.get("EXACT_MATCH") + 1000L);

            // 3. SCN-41: BANK_DUPLICATE (1,000 groups: 1 internal row, 2 bank rows = 1,000 internal, 2,000 bank)
            // Primary matches EXACT_MATCH (Order 100), duplicate matches BANK_DUPLICATE_DETECTED (Order 350)
            for (int i = 0; i < 1000; i++) {
                String ref = "PAY-BNKDUP-" + i;
                String stl = "STL-BNKDUP-" + i;
                String amt = String.format(Locale.ROOT, "%.2f", 140.00 + (i % 40));
                LocalDate date = BASE_DATE.plusDays(i % 20);

                internalWriter.write(String.format(Locale.ROOT, "TXN%07d,%s,%s,Checkout order,%s,INR,SUCCESS,%s\n",
                        txnSeq++, ref, date, amt, stl));

                // Primary bank credit
                bankWriter.write(String.format(Locale.ROOT, "%s,BNK%07d,%s,%s,Primary bank credit,%s,0.00,INR\n",
                        date, bnkSeq++, ref, stl, amt));
                // Duplicate bank credit
                bankWriter.write(String.format(Locale.ROOT, "%s,BNK%07d,%s,%s,Duplicate bank credit posting,%s,0.00,INR\n",
                        date, bnkSeq++, ref, stl, amt));
            }
            expectedCounts.put("BANK_DUPLICATE_DETECTED", 1000L);
            expectedCounts.put("EXACT_MATCH", expectedCounts.get("EXACT_MATCH") + 1000L);

            // 4. SCN-02: DATE_TOLERANCE_MATCH (4,000 pairs = 4k internal, 4k bank) -> DATE_MISMATCH (Order 200)
            for (int i = 0; i < 4000; i++) {
                String ref = "PAY-DATE-" + i;
                String stl = "STL-DATE-" + i;
                String amt = String.format(Locale.ROOT, "%.2f", 200.00 + (i % 300) + 0.25);
                LocalDate intDate = BASE_DATE.plusDays(i % 20);
                LocalDate bnkDate = intDate.plusDays(1 + (i % 2)); // 1 or 2 days lag (<= 3 days tolerance)

                internalWriter.write(String.format(Locale.ROOT, "TXN%07d,%s,%s,Delayed settlement order,%s,INR,SUCCESS,%s\n",
                        txnSeq++, ref, intDate, amt, stl));
                bankWriter.write(String.format(Locale.ROOT, "%s,BNK%07d,%s,%s,Payout credit with date lag,%s,0.00,INR\n",
                        bnkDate, bnkSeq++, ref, stl, amt));
            }
            expectedCounts.put("DATE_MISMATCH", 4000L);

            // 5. SCN-03: AMOUNT_ROUNDING_MATCH (2,000 pairs = 2k internal, 2k bank) -> AMOUNT_MISMATCH (Order 300)
            for (int i = 0; i < 2000; i++) {
                String ref = "PAY-RND-" + i;
                String stl = "STL-RND-" + i;
                double baseAmt = 350.00 + (i % 200);
                double diff = 0.50 + ((i % 4) * 0.50); // 0.50, 1.00, 1.50, 2.00
                String intAmt = String.format(Locale.ROOT, "%.2f", baseAmt);
                String bnkAmt = String.format(Locale.ROOT, "%.2f", baseAmt + diff);
                LocalDate date = BASE_DATE.plusDays(i % 20);

                internalWriter.write(String.format(Locale.ROOT, "TXN%07d,%s,%s,Rounding order,%s,INR,SUCCESS,%s\n",
                        txnSeq++, ref, date, intAmt, stl));
                bankWriter.write(String.format(Locale.ROOT, "%s,BNK%07d,%s,%s,Payout credit with rounding difference,%s,0.00,INR\n",
                        date, bnkSeq++, ref, stl, bnkAmt));
            }
            expectedCounts.put("AMOUNT_MISMATCH", 2000L);

            // 6. SCN-05: BATCH_SETTLEMENT (1,000 groups: 2 internal rows = 1 bank row; 2,000 internal, 1,000 bank) -> BATCH_SETTLEMENT (Order 400)
            for (int i = 0; i < 1000; i++) {
                String stl = "SETL-BATCH-" + i;
                LocalDate date = BASE_DATE.plusDays(i % 20);
                String amt1 = String.format(Locale.ROOT, "%.2f", 100.00 + (i % 50));
                String amt2 = String.format(Locale.ROOT, "%.2f", 150.00 + (i % 50));
                double sum = (100.00 + (i % 50)) + (150.00 + (i % 50));
                String bnkAmt = String.format(Locale.ROOT, "%.2f", sum);

                internalWriter.write(String.format(Locale.ROOT, "TXN%07d,PAY-BATCH-A-%d,%s,Batch item 1,%s,INR,SUCCESS,%s\n",
                        txnSeq++, i, date, amt1, stl));
                internalWriter.write(String.format(Locale.ROOT, "TXN%07d,PAY-BATCH-B-%d,%s,Batch item 2,%s,INR,SUCCESS,%s\n",
                        txnSeq++, i, date, amt2, stl));

                bankWriter.write(String.format(Locale.ROOT, "%s,BNK%07d,,%s,Consolidated batch payout credit,%s,0.00,INR\n",
                        date, bnkSeq++, stl, bnkAmt));
            }
            expectedCounts.put("BATCH_SETTLEMENT", 1000L);

            // 7. SCN-07: REFUND_MATCH (1,000 pairs = 1,000 internal, 1,000 bank) -> REFUND_MATCH (Order 500)
            for (int i = 0; i < 1000; i++) {
                String ref = "PAY-REF-" + i;
                String stl = "STL-REF-" + i;
                String amt = String.format(Locale.ROOT, "%.2f", 75.00 + (i % 60));
                LocalDate date = BASE_DATE.plusDays(i % 20);

                internalWriter.write(String.format(Locale.ROOT, "TXN%07d,%s,%s,Customer refund processed,%s,INR,REFUNDED,%s\n",
                        txnSeq++, ref, date, amt, stl));
                bankWriter.write(String.format(Locale.ROOT, "%s,BNK%07d,%s,%s,Customer refund debit,0.00,%s,INR\n",
                        date, bnkSeq++, ref, stl, amt));
            }
            expectedCounts.put("REFUND_MATCH", 1000L);

            // 8. SCN-39: REFUND_PENDING_BANK (1,000 internal rows with REFUND_INITIATED, no bank row) -> REFUND_PENDING_BANK (Order 500)
            for (int i = 0; i < 1000; i++) {
                String ref = "PAY-REFPEND-" + i;
                String stl = "STL-REFPEND-" + i;
                String amt = String.format(Locale.ROOT, "%.2f", 65.00 + (i % 50));
                LocalDate date = BASE_DATE.plusDays(i % 20);

                internalWriter.write(String.format(Locale.ROOT, "TXN%07d,%s,%s,Refund initiated pending bank debit,%s,INR,REFUND_INITIATED,%s\n",
                        txnSeq++, ref, date, amt, stl));
            }
            expectedCounts.put("REFUND_PENDING_BANK", 1000L);

            // 9. SCN-08: BANK_SERVICE_CHARGE (1,000 bank debits with keyword "fee" or "charge") -> BANK_SERVICE_CHARGE (Order 600)
            for (int i = 0; i < 1000; i++) {
                String amt = String.format(Locale.ROOT, "%.2f", 10.00 + (i % 25));
                LocalDate date = BASE_DATE.plusDays(i % 25);
                bankWriter.write(String.format(Locale.ROOT, "%s,BNK%07d,BNK-FEE-%d,STL-FEE-%d,Monthly account maintenance fee debit,0.00,%s,INR\n",
                        date, bnkSeq++, i, i, amt));
            }
            expectedCounts.put("BANK_SERVICE_CHARGE", 1000L);

            // 10. SCN-11: UNEXPECTED_BANK_DEBIT (1,000 bank debits with keyword "chargeback") -> UNEXPECTED_BANK_DEBIT (Order 900)
            for (int i = 0; i < 1000; i++) {
                String amt = String.format(Locale.ROOT, "%.2f", 50.00 + (i % 80));
                LocalDate date = BASE_DATE.plusDays(i % 25);
                bankWriter.write(String.format(Locale.ROOT, "%s,BNK%07d,BNK-CHG-%d,STL-CHG-%d,Dispute chargeback debit without approval,0.00,%s,INR\n",
                        date, bnkSeq++, i, i, amt));
            }
            expectedCounts.put("UNEXPECTED_BANK_DEBIT", 1000L);

            // 11. SCN-10: MISSING_IN_BANK (6,000 internal rows with SUCCESS and no bank match) -> MISSING_IN_BANK (Order 910)
            for (int i = 0; i < 6000; i++) {
                String amt = String.format(Locale.ROOT, "%.2f", 90.00 + (i % 120));
                LocalDate date = BASE_DATE.plusDays(i % 25);
                internalWriter.write(String.format(Locale.ROOT, "TXN%07d,PAY-ORPHAN-%d,%s,Unsettled internal transaction,%s,INR,SUCCESS,STL-ORPHAN-%d\n",
                        txnSeq++, i, date, amt, i));
            }
            expectedCounts.put("MISSING_IN_BANK", 6000L);

            // 12. SCN-09: MISSING_IN_INTERNAL (7,000 bank credits with no internal match) -> MISSING_IN_INTERNAL (Order 920)
            for (int i = 0; i < 7000; i++) {
                String amt = String.format(Locale.ROOT, "%.2f", 80.00 + (i % 100));
                LocalDate date = BASE_DATE.plusDays(i % 25);
                bankWriter.write(String.format(Locale.ROOT, "%s,BNK%07d,BNK-UNEXP-%d,STL-UNEXP-%d,Direct inward NEFT credit from customer,%s,0.00,INR\n",
                        date, bnkSeq++, i, i, amt));
            }
            expectedCounts.put("MISSING_IN_INTERNAL", 7000L);

            // 13. SCN-43: IGNORED_FAILED_STATUS (1,000 internal rows with payment_status=FAILED) -> IGNORED_FAILED_STATUS (Order 10)
            for (int i = 0; i < 1000; i++) {
                String ref = "PAY-FAIL-" + i;
                String stl = "STL-FAIL-" + i;
                String amt = String.format(Locale.ROOT, "%.2f", 60.00 + (i % 30));
                LocalDate date = BASE_DATE.plusDays(i % 20);

                internalWriter.write(String.format(Locale.ROOT, "TXN%07d,%s,%s,Checkout payment declined at gateway,%s,INR,FAILED,%s\n",
                        txnSeq++, ref, date, amt, stl));
            }
            expectedCounts.put("IGNORED_FAILED_STATUS", 1000L);
        }

        // Write expected_counts.json
        StringBuilder json = new StringBuilder("{\n");
        int count = 0;
        long totalExpected = 0;
        for (Map.Entry<String, Long> entry : expectedCounts.entrySet()) {
            json.append(String.format(Locale.ROOT, "  \"%s\": %d%s\n", entry.getKey(), entry.getValue(),
                    (++count < expectedCounts.size() ? "," : "")));
            totalExpected += entry.getValue();
        }
        json.append("}\n");
        Files.writeString(expectedPath, json.toString());

        System.out.printf("Successfully generated:%n  - %s (50,000 rows)%n  - %s (50,000 rows)%n  - %s (%d total expected results)%n",
                internalPath, bankPath, expectedPath, totalExpected);
    }
}
