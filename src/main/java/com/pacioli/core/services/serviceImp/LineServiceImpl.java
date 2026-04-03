package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.Line;
import com.pacioli.core.repositories.LineRepository;
import com.pacioli.core.services.AuditService;
import com.pacioli.core.services.LineService;
import com.pacioli.core.services.UserService;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.Objects;

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

    // ✅ Méthode utilitaire pour récupérer le cabinet cible
    private Long getTargetCabinetId(Line line) {
        if (line != null && line.getEcriture() != null &&
                line.getEcriture().getPiece() != null &&
                line.getEcriture().getPiece().getDossier() != null &&
                line.getEcriture().getPiece().getDossier().getCabinet() != null) {
            return line.getEcriture().getPiece().getDossier().getCabinet().getId();
        }
        return null;
    }

    // ✅ Méthode utilitaire pour récupérer le nom du cabinet cible
    private String getTargetCabinetName(Line line) {
        if (line != null && line.getEcriture() != null &&
                line.getEcriture().getPiece() != null &&
                line.getEcriture().getPiece().getDossier() != null &&
                line.getEcriture().getPiece().getDossier().getCabinet() != null) {
            return line.getEcriture().getPiece().getDossier().getCabinet().getName();
        }
        return null;
    }

    @Override
    public Line addLine(@NonNull Line line) {
        Line savedLine = lineRepository.save(Objects.requireNonNull(line, "line"));

        // ✅ Audit avec cabinet cible
        Long targetCabinetId = getTargetCabinetId(savedLine);
        String targetCabinetName = getTargetCabinetName(savedLine);

        auditService.logSuccessWithTargetCabinet(
                userService.getCurrentUser(),
                "CREATE",
                "Line",
                savedLine.getId(),
                "Line-" + savedLine.getId(),
                null,
                savedLine,
                targetCabinetId,
                targetCabinetName
        );

        return savedLine;
    }

    @Override
    public Line updateLine(@NonNull Long id, @NonNull Line updatedLine) {
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

            // ✅ Audit avec cabinet cible
            Long targetCabinetId = getTargetCabinetId(savedLine);
            String targetCabinetName = getTargetCabinetName(savedLine);

            auditService.logSuccessWithTargetCabinet(
                    userService.getCurrentUser(),
                    "UPDATE",
                    "Line",
                    id,
                    "Line-" + id,
                    oldLine,
                    savedLine,
                    targetCabinetId,
                    targetCabinetName
            );

            return savedLine;
        }).orElseThrow(() -> {
            // Audit échec (pas de cabinet cible car ligne non trouvée)
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
    public void deleteLine(@NonNull Long id) {
        Line line = lineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Line not found with ID: " + id));

        // ✅ Audit avant suppression avec cabinet cible
        Long targetCabinetId = getTargetCabinetId(line);
        String targetCabinetName = getTargetCabinetName(line);

        auditService.logSuccessWithTargetCabinet(
                userService.getCurrentUser(),
                "DELETE",
                "Line",
                id,
                "Line-" + id,
                line,
                null,
                targetCabinetId,
                targetCabinetName
        );

        lineRepository.deleteById(id);
    }
}