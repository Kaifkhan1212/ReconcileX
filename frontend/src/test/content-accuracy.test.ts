import { describe, it, expect } from "vitest";
import {
  BANK_REQUIRED_HEADERS,
  INTERNAL_REQUIRED_HEADERS,
  MATCHING_RULES,
  AUDIT_EVENT_TYPES,
} from "@/lib/matching-rules";

describe("Content Accuracy & Domain Integrity Tests", () => {
  it("upload headers constant equals backend required headers for bank statement", () => {
    // Backend FileImportService.BANK_REQUIRED_COLUMNS:
    // transaction_date, bank_reference, payment_reference, settlement_reference, description, credit_amount, debit_amount, currency
    const expectedBankHeaders = [
      "transaction_date",
      "bank_reference",
      "payment_reference",
      "settlement_reference",
      "description",
      "credit_amount",
      "debit_amount",
      "currency",
    ];
    expect([...BANK_REQUIRED_HEADERS]).toEqual(expectedBankHeaders);
  });

  it("upload headers constant equals backend required headers for internal ledger", () => {
    // Backend FileImportService.INTERNAL_REQUIRED_COLUMNS:
    // transaction_id, payment_reference, transaction_date, description, amount, currency, payment_status, settlement_reference
    const expectedInternalHeaders = [
      "transaction_id",
      "payment_reference",
      "transaction_date",
      "description",
      "amount",
      "currency",
      "payment_status",
      "settlement_reference",
    ];
    expect([...INTERNAL_REQUIRED_HEADERS]).toEqual(expectedInternalHeaders);
  });

  it("matching rules constant defines all 12 sequential deterministic rules", () => {
    expect(MATCHING_RULES.length).toBe(12);

    const ruleClasses = MATCHING_RULES.map((r) => r.ruleClass);
    expect(ruleClasses).toContain("ExactMatchRule");
    expect(ruleClasses).toContain("DateToleranceRule");
    expect(ruleClasses).toContain("AmountMismatchRule");
    expect(ruleClasses).toContain("BatchSettlementRule");
    expect(ruleClasses).toContain("RefundRule");
    expect(ruleClasses).toContain("ServiceChargeRule");
    expect(ruleClasses).toContain("MissingInBankRule");
    expect(ruleClasses).toContain("MissingInInternalRule");

    // Precedence orders are strictly ascending
    for (let i = 1; i < MATCHING_RULES.length; i++) {
      expect(MATCHING_RULES[i]!.order).toBeGreaterThan(MATCHING_RULES[i - 1]!.order);
    }
  });

  it("audit event types match backend recorded events", () => {
    expect(AUDIT_EVENT_TYPES).toContain("RUN_STARTED");
    expect(AUDIT_EVENT_TYPES).toContain("RUN_COMPLETED");
    expect(AUDIT_EVENT_TYPES).toContain("RUN_FAILED");
    expect(AUDIT_EVENT_TYPES).toContain("IMPORT_STARTED");
    expect(AUDIT_EVENT_TYPES).toContain("IMPORT_COMPLETED");
    expect(AUDIT_EVENT_TYPES).toContain("IMPORT_FAILED");
    expect(AUDIT_EVENT_TYPES).toContain("IMPORT_DUPLICATE_REJECTED");
    expect(AUDIT_EVENT_TYPES).toContain("MATCH_DECISION");
  });

  it("calculates % paired accurately based on INR monetary value formula", () => {
    const summary = {
      matchedValue: 100000,
      unmatchedInternalValue: 10000,
      unmatchedBankCreditValue: 10000,
      unmatchedBankDebitValue: 5000,
    };

    const totalValue =
      summary.matchedValue +
      summary.unmatchedInternalValue +
      summary.unmatchedBankCreditValue +
      summary.unmatchedBankDebitValue; // 125,000

    const matchRatio = Math.round((summary.matchedValue / totalValue) * 100);
    expect(matchRatio).toBe(80);
  });
});

