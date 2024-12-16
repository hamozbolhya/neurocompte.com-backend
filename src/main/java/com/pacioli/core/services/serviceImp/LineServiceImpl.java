package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.Line;
import com.pacioli.core.repositories.LineRepository;
import com.pacioli.core.services.LineService;
import org.springframework.stereotype.Service;

@Service
public class LineServiceImpl implements LineService {
    private final LineRepository lineRepository;

    public LineServiceImpl(LineRepository lineRepository) {
        this.lineRepository = lineRepository;
    }

    @Override
    public Line addLine(Line line) {
        return lineRepository.save(line);
    }

    @Override
    public Line updateLine(Long id, Line updatedLine) {
        return lineRepository.findById(id).map(existingLine -> {
            existingLine.setAccount(updatedLine.getAccount());
            existingLine.setLabel(updatedLine.getLabel());
            existingLine.setDebit(updatedLine.getDebit());
            existingLine.setCredit(updatedLine.getCredit());
            return lineRepository.save(existingLine);
        }).orElseThrow(() -> new RuntimeException("Line not found with ID: " + id));
    }

    @Override
    public void deleteLine(Long id) {
        lineRepository.deleteById(id);
    }
}