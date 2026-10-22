package com.reconcilex.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class ReconciliationRuleEngine {

    private final List<MatchingRule> rules;

    public ReconciliationRuleEngine(List<MatchingRule> rules) {
        List<MatchingRule> sorted = new ArrayList<>(rules);
        sorted.sort(Comparator.comparingInt(MatchingRule::getOrder));
        this.rules = Collections.unmodifiableList(sorted);
    }

    public void execute(ReconciliationContext context) {
        for (MatchingRule rule : rules) {
            rule.apply(context);
        }
    }

    public List<MatchingRule> getRules() {
        return rules;
    }
}
