package com.pacioli.core.DTO;

import lombok.Data;

import java.util.Date;

@Data
public class FactureDataDTO {
    private Long id;
    private String invoiceNumber;
    private Date invoiceDate;
    private Double totalTTC;
    private Double totalHT;
    private Double totalTVA;
    private Double taxRate;
    private String tier;
    private String ice;
}
