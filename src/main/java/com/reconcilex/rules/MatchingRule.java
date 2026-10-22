package com.reconcilex.rules;

public interface MatchingRule {

    String getName();

    int getOrder();

    void apply(ReconciliationContext context);
}
