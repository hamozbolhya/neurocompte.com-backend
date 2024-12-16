package com.pacioli.core.DTO;

import com.pacioli.core.models.Dossier;
import com.pacioli.core.models.Exercise;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class DossierRequest {
    private Dossier dossier;
    @JsonProperty("exercise") // Ensure it matches the JSON payload
    private List<Exercise> exercises;
}
