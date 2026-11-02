-- V6: Fix row-level uniqueness to be per-import instead of globally unique.
--
-- The original schema enforced UNIQUE (bank_reference) and UNIQUE (transaction_id)
-- globally across all imports. This caused every re-upload of the same 50K CSV
-- to skip all rows because the bank_reference / transaction_id values from previous
-- imports (or previous failed attempts) already exist in the table.
--
-- The correct design:
--   - A bank_reference is unique WITHIN a single import (same file shouldn't have dups)
--   - The same bank_reference CAN appear in a different import (e.g. re-upload after cleanup)
--
-- We replace the global unique constraint with a composite one: (file_import_id, bank_reference).
-- The existing (file_import_id, source_row_number) constraint is kept as-is.

-- 1. Drop the globally-unique bank_reference constraint
ALTER TABLE bank_statement_rows
    DROP CONSTRAINT IF EXISTS uq_bank_statement_bank_ref;

-- 2. Add per-import uniqueness: bank_reference must be unique within a single import
ALTER TABLE bank_statement_rows
    ADD CONSTRAINT uq_bank_ref_per_import
        UNIQUE (file_import_id, bank_reference);

-- 3. Drop the globally-unique transaction_id constraint
ALTER TABLE internal_transactions
    DROP CONSTRAINT IF EXISTS uq_internal_transactions_txn_id;

-- 4. Add per-import uniqueness: transaction_id must be unique within a single import
ALTER TABLE internal_transactions
    ADD CONSTRAINT uq_txn_id_per_import
        UNIQUE (file_import_id, transaction_id);

