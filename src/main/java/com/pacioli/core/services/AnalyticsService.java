package com.pacioli.core.services;

import com.pacioli.core.DTO.analytics.AdminAnalyticsDTO;

import java.time.LocalDate;

public interface AnalyticsService {
    AdminAnalyticsDTO getAdminAnalytics();
    AdminAnalyticsDTO getAdminAnalyticsForPeriod(LocalDate startDate, LocalDate endDate);
}
