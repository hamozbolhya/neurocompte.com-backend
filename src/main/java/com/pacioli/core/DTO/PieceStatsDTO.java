package com.pacioli.core.DTO;

public class PieceStatsDTO {
    private Long dossierId;
    private String dossierName;
    private Long totalCount;
    private Long uploadedCount;
    private Long rejectedCount;
    private Long processedCount;
    private String dossierCurrency;
    private String countryCode;
    private Long processingCount;

    // This constructor must exactly match the parameters in the repository query
    public PieceStatsDTO(
            Long dossierId,
            String dossierName,
            Long totalCount,
            Long uploadedCount,
            Long processedCount,
            Long rejectedCount,
            Long processingCount,
            String dossierCurrency,
            String countryCode) {
        this.dossierId = dossierId;
        this.dossierName = dossierName;
        this.totalCount = totalCount;
        this.uploadedCount = uploadedCount;
        this.processedCount = processedCount;
        this.rejectedCount = rejectedCount;
        this.processingCount = processingCount;
        this.dossierCurrency = dossierCurrency;
        this.countryCode = countryCode;
    }

    // Default constructor for serialization/deserialization
    public PieceStatsDTO() {
    }

    // Getters and setters
    public Long getDossierId() {
        return dossierId;
    }

    public void setDossierId(Long dossierId) {
        this.dossierId = dossierId;
    }

    public String getDossierName() {
        return dossierName;
    }

    public void setDossierName(String dossierName) {
        this.dossierName = dossierName;
    }

    public Long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Long totalCount) {
        this.totalCount = totalCount;
    }

    public Long getUploadedCount() {
        return uploadedCount;
    }

    public void setUploadedCount(Long uploadedCount) {
        this.uploadedCount = uploadedCount;
    }

    public Long getProcessedCount() {
        return processedCount;
    }

    public void setProcessedCount(Long processedCount) {
        this.processedCount = processedCount;
    }

    public Long getRejectedCount() {
        return rejectedCount;
    }

    public void setRejectedCount(Long rejectedCount) {
        this.rejectedCount = rejectedCount;
    }


    // Add getter and setter for processingCount
    public Long getProcessingCount() {
        return processingCount;
    }

    public void setProcessingCount(Long processingCount) {
        this.processingCount = processingCount;
    }

    public String getDossierCurrency() {
        return dossierCurrency;
    }

    public void setDossierCurrency(String dossierCurrency) {
        this.dossierCurrency = dossierCurrency;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    @Override
    public String toString() {
        return "PieceStatsDTO{" +
                "dossierId=" + dossierId +
                ", dossierName='" + dossierName + '\'' +
                ", totalCount=" + totalCount +
                ", uploadedCount=" + uploadedCount +
                ", processedCount=" + processedCount +
                ", rejectedCount=" + rejectedCount +
                ", processingCount=" + processingCount +
                ", dossierCurrency='" + dossierCurrency + '\'' +
                ", countryCode='" + countryCode + '\'' +
                '}';
    }
}