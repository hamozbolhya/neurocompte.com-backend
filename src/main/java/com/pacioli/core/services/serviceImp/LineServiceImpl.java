package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.Line;
import com.pacioli.core.repositories.LineRepository;
import com.pacioli.core.services.AuditService;
import com.pacioli.core.services.LineService;
import com.pacioli.core.services.UserService;
import org.springframework.stereotype.Service;

@Service
public class LineServiceImpl implements LineService {
    private final LineRepository lineRepository;
    private final AuditService auditService;
    private final UserService userService;

    public LineServiceImpl(LineRepository lineRepository,
                           AuditService auditService,
                           UserService userService) {
        this.lineRepository = lineRepository;
        this.auditService = auditService;
        this.userService = userService;
    }

    @Override
    public Line addLine(Line line) {
        Line savedLine = lineRepository.save(line);

        // Audit
        auditService.logSuccess(
                userService.getCurrentUser(),
                "CREATE",
                "Line",
                savedLine.getId(),
                "Line-" + savedLine.getId(),
                null,
                savedLine
        );

        return savedLine;
    }

    @Override
    public Line updateLine(Long id, Line updatedLine) {
        return lineRepository.findById(id).map(existingLine -> {
            // Sauvegarder l'ancien état pour l'audit
            Line oldLine = new Line();
            oldLine.setId(existingLine.getId());
            oldLine.setAccount(existingLine.getAccount());
            oldLine.setLabel(existingLine.getLabel());
            oldLine.setDebit(existingLine.getDebit());
            oldLine.setCredit(existingLine.getCredit());

            // Mise à jour
            existingLine.setAccount(updatedLine.getAccount());
            existingLine.setLabel(updatedLine.getLabel());
            existingLine.setDebit(updatedLine.getDebit());
            existingLine.setCredit(updatedLine.getCredit());

            Line savedLine = lineRepository.save(existingLine);

            // Audit
            auditService.logSuccess(
                    userService.getCurrentUser(),
                    "UPDATE",
                    "Line",
                    id,
                    "Line-" + id,
                    oldLine,
                    savedLine
            );

            return savedLine;
        }).orElseThrow(() -> {
            // Audit échec
            auditService.logFailure(
                    userService.getCurrentUser(),
                    "UPDATE",
                    "Line",
                    id,
                    "Line-" + id,
                    "Line not found with ID: " + id
            );
            return new RuntimeException("Line not found with ID: " + id);
        });
    }

    @Override
    public void deleteLine(Long id) {
        Line line = lineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Line not found with ID: " + id));

        // Audit avant suppression
        auditService.logSuccess(
                userService.getCurrentUser(),
                "DELETE",
                "Line",
                id,
                "Line-" + id,
                line,
                null
        );

        lineRepository.deleteById(id);
    }
}