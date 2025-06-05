package com.pacioli.core.DTO;

import lombok.Data;
import java.time.LocalDate;

@Data
public class LigneEcritureDTO {
    private Long id;
    private String label;
    private Double debit;
    private Double credit;
    private AccountDTO account;

    // Currency information
    private Double originalDebit;
    private Double originalCredit;
    private String originalCurrency;
    private Double convertedDebit;
    private Double convertedCredit;
    private String convertedCurrency;
    private Double exchangeRate;
    private LocalDate exchangeRateDate;

    private Double usdDebit;
    private Double usdCredit;

}
