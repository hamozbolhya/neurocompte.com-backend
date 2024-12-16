package com.pacioli.core.services;

import com.pacioli.core.models.Line;

public interface LineService {
    Line addLine(Line line);
    Line updateLine(Long id, Line updatedLine);
    void deleteLine(Long id);
}
