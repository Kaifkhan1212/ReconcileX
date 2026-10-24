package com.reconcilex.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "reconcilex.matching")
public class MatchingProperties {

    private int dateToleranceDays = 3;

    private List<String> serviceChargeKeywords = List.of(
            "charge", "fee", "maintenance", "IMPS", "SMS", "mandate", "POS", "folio", "cheque book", "forex"
    );

    private List<String> unexpectedDebitKeywords = List.of(
            "chargeback", "dispute", "reversal"
    );

    public int getDateToleranceDays() {
        return dateToleranceDays;
    }

    public void setDateToleranceDays(int dateToleranceDays) {
        this.dateToleranceDays = dateToleranceDays;
    }

    public List<String> getServiceChargeKeywords() {
        return serviceChargeKeywords;
    }

    public void setServiceChargeKeywords(List<String> serviceChargeKeywords) {
        this.serviceChargeKeywords = serviceChargeKeywords;
    }

    public List<String> getUnexpectedDebitKeywords() {
        return unexpectedDebitKeywords;
    }

    public void setUnexpectedDebitKeywords(List<String> unexpectedDebitKeywords) {
        this.unexpectedDebitKeywords = unexpectedDebitKeywords;
    }
}
