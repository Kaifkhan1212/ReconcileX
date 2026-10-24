package com.reconcilex.rules;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.reconcilex.config.MatchingProperties;
import com.reconcilex.entity.BankStatementRow;
import com.reconcilex.entity.ReconciliationResultType;

/**
 * Order 900 – unmatched bank DEBIT rows whose {@code description} column
 * contains a configurable keyword (chargeback, dispute, reversal) as a whole
 * word (case-insensitive) are classified as UNEXPECTED_BANK_DEBIT.
 *
 * Runs after ServiceChargeRule so a chargeback is never accidentally matched
 * by the service-charge keyword "charge".
 *
 * Note: the {@code payment_reference} column ("CHG-BCK-771") and {@code bank_reference}
 * column ("BNK2034") are NOT inspected for keywords; only the {@code description}
 * column is used, matched whole-word and case-insensitive against the configured keywords.
 *
 * The keyword list is read from
 * {@code reconcilex.matching.unexpected-debit-keywords}.
 */
@Component
public class UnexpectedDebitRule implements MatchingRule {

    public static final String RULE_NAME = "UnexpectedDebitRule";
    public static final String RULE_CODE = "RULE_UNEXPECTED_DEBIT";

    private final List<Pattern> keywordPatterns;

    public UnexpectedDebitRule(MatchingProperties props) {
        this.keywordPatterns = buildPatterns(props.getUnexpectedDebitKeywords());
    }

    static List<Pattern> buildPatterns(List<String> keywords) {
        return keywords.stream()
                .map(kw -> Pattern.compile(
                        "(?i)(?<![\\w])(" + Pattern.quote(kw) + ")(?![\\w])"
                ))
                .toList();
    }

    @Override
    public String getName() {
        return RULE_NAME;
    }

    @Override
    public int getOrder() {
        return MatchingRuleOrder.UNEXPECTED_DEBIT;
    }

    @Override
    public void apply(ReconciliationContext context) {
        for (BankStatementRow b : context.getBankRows()) {
            if (context.isBankRowMatched(b.getId())) {
                continue;
            }
            // Only debit rows
            if (b.getDebitAmount() == null || b.getDebitAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String description = b.getDescription();
            if (description == null || description.isBlank()) {
                continue;
            }
            boolean matched = false;
            for (Pattern p : keywordPatterns) {
                if (p.matcher(description).find()) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                continue;
            }
            String explanation = String.format(
                    "Bank debit %s (%s %s) is an unexpected debit (chargeback/dispute/reversal) based on description: \"%s\".",
                    b.getBankReference(),
                    b.getDebitAmount().toPlainString(),
                    b.getCurrency(),
                    description
            );
            context.addMatch(MatchOutcome.bankOnly(
                    ReconciliationResultType.UNEXPECTED_BANK_DEBIT,
                    RULE_NAME,
                    RULE_CODE,
                    explanation,
                    b
            ));
        }
    }
}
