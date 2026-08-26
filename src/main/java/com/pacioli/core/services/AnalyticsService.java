package com.pacioli.core.services;

import com.pacioli.core.DTO.analytics.AdminAnalyticsDTO;
import org.springframework.lang.NonNull;

import java.time.LocalDate;
import java.util.Map;

public interface AnalyticsService {
    AdminAnalyticsDTO getAdminAnalytics();
    AdminAnalyticsDTO getAdminAnalyticsForPeriod(LocalDate startDate, LocalDate endDate);
    Map<String, Object> getCabinetAnalytics(@NonNull Long cabinetId);
    Map<String, Object> getCabinetAnalyticsForPeriod(@NonNull Long cabinetId, LocalDate startDate, LocalDate endDate);
}
