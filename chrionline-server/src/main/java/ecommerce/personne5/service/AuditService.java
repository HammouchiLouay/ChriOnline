package ecommerce.personne5.service;

import common.ChrionlineLog;
import ecommerce.personne5.model.AuditLog;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Journalise les actions en mémoire et peut exporter vers un fichier texte. */
public class AuditService {
    private final List<AuditLog> logs = new ArrayList<>();

    public void enregistrerAction(String acteur, String action, String details) {
        AuditLog log = new AuditLog(LocalDateTime.now().toString(), acteur, action, details);
        logs.add(log);
    }

    public void afficherLogs() {
        System.out.println("\n===== AUDIT LOGS =====");
        for (AuditLog log : logs) {
            System.out.println(log);
        }
        System.out.println("======================\n");
    }

    public void sauvegarderLogsFichier() {
        try {
            var path = ChrionlineLog.dotChrionlineDir().resolve("audit_logs.txt");
            Files.createDirectories(path.getParent());
            try (PrintWriter writer = new PrintWriter(new FileWriter(path.toFile(), true))) {
                for (AuditLog log : logs) {
                    writer.println(log);
                }
            }
        } catch (IOException e) {
            ChrionlineLog.err("Erreur sauvegarde audit logs : " + e.getMessage());
        }
    }
}