package com.reconcilex.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "reconcilex.matching")
public class MatchingProperties {

    private int dateToleranceDays = 3;

    public int getDateToleranceDays() {
        return dateToleranceDays;
    }

    public void setDateToleranceDays(int dateToleranceDays) {
        this.dateToleranceDays = dateToleranceDays;
    }
}
