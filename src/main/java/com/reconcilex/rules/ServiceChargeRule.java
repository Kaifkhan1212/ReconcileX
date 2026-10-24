package com.reconcilex.rules;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.reconcilex.config.MatchingProperties;
import com.reconcilex.entity.BankStatementRow;
import com.reconcilex.entity.ReconciliationResultType;

/**
 * Order 600 – unmatched bank DEBIT rows whose {@code description} column
 * contains at least one configurable keyword as a whole word (case-insensitive)
 * are classified as BANK_SERVICE_CHARGE.
 *
 * Whole-word matching: a keyword must be surrounded by non-word characters or
 * be at the start/end of the string.  This ensures "charge" matches
 * "maintenance charges" but NOT "chargeback".
 *
 * The keyword list is read from
 * {@code reconcilex.matching.service-charge-keywords}.
 */
@Component
public class ServiceChargeRule implements MatchingRule {

    public static final String RULE_NAME = "ServiceChargeRule";
    public static final String RULE_CODE = "RULE_SERVICE_CHARGE";

    private final List<Pattern> keywordPatterns;

    public ServiceChargeRule(MatchingProperties props) {
        this.keywordPatterns = buildPatterns(props.getServiceChargeKeywords());
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
        return MatchingRuleOrder.SERVICE_CHARGE;
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
                    "Bank debit %s (%s %s) classified as service charge based on description: \"%s\".",
                    b.getBankReference(),
                    b.getDebitAmount().toPlainString(),
                    b.getCurrency(),
                    description
            );
            context.addMatch(MatchOutcome.bankOnly(
                    ReconciliationResultType.BANK_SERVICE_CHARGE,
                    RULE_NAME,
                    RULE_CODE,
                    explanation,
                    b
            ));
        }
    }
}
