package com.pacioli.core.DTO;

import com.pacioli.core.models.Exercise;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DossierRequest {

    private DossierDTO dossier;
    private List<Exercise> exercises = new ArrayList<>();
}
