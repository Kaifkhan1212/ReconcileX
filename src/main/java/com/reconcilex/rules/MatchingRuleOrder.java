package com.reconcilex.rules;

public final class MatchingRuleOrder {

    private MatchingRuleOrder() {
    }

    public static final int FAILED_STATUS        = 10;
    public static final int INTERNAL_DUPLICATE   = 50;
    public static final int EXACT_MATCH          = 100;
    public static final int DATE_TOLERANCE       = 200;
    public static final int AMOUNT_MISMATCH      = 300;
    public static final int BANK_DUPLICATE       = 350;
    public static final int BATCH_SETTLEMENT     = 400;
    public static final int REFUND               = 500;
    public static final int SERVICE_CHARGE       = 600;
    public static final int UNEXPECTED_DEBIT     = 900;
    public static final int MISSING_IN_BANK      = 910;
    public static final int MISSING_IN_INTERNAL  = 920;
}
