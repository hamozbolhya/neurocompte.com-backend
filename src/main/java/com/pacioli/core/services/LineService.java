package com.pacioli.core.services;

import com.pacioli.core.models.Line;
import org.springframework.lang.NonNull;

public interface LineService {
    Line addLine(@NonNull Line line);
    Line updateLine(@NonNull Long id, @NonNull Line updatedLine);
    void deleteLine(@NonNull Long id);
}
