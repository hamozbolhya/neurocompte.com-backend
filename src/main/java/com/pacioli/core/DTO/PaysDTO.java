package com.pacioli.core.DTO;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaysDTO {
    private String country;
    private String code;
    private CurrencyDTO currency;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrencyDTO {
        private String code;
        private String name;
    }
}