package com.pacioli.core.DTO;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ExerciseRequest {
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean active;
}