ALTER TABLE reconciliation_results
    ADD COLUMN IF NOT EXISTS rule_name VARCHAR(80),
    ADD COLUMN IF NOT EXISTS amount_difference NUMERIC(19, 2);
