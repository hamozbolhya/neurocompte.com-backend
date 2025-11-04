package com.pacioli.core.services;

import com.pacioli.core.DTO.analytics.AdminAnalyticsDTO;

import java.time.LocalDate;
import java.util.Map;

public interface AnalyticsService {
    AdminAnalyticsDTO getAdminAnalytics();
    AdminAnalyticsDTO getAdminAnalyticsForPeriod(LocalDate startDate, LocalDate endDate);
    Map<String, Object> getCabinetAnalytics(Long cabinetId);
    Map<String, Object> getCabinetAnalyticsForPeriod(Long cabinetId, LocalDate startDate, LocalDate endDate);
}
