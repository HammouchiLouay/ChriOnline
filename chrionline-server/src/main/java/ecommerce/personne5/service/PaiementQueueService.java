package ecommerce.personne5.service;

import ecommerce.personne5.model.Paiement;

import java.util.LinkedList;
import java.util.Queue;

/** File FIFO en mémoire des paiements simulés (démo). */
public class PaiementQueueService {
    private final Queue<Paiement> filePaiements = new LinkedList<>();

    public void ajouterPaiement(Paiement paiement) {
        filePaiements.offer(paiement);
        System.out.println("Paiement ajoute dans la file.");
    }

    public Paiement traiterProchainPaiement() {
        Paiement paiement = filePaiements.poll();
        if (paiement == null) {
            System.out.println("Aucun paiement dans la file.");
            return null;
        }
        System.out.println("Paiement retire de la file : " + paiement.getIdPaiement());
        return paiement;
    }

    public void afficherTailleFile() {
        System.out.println("Nombre de paiements dans la file : " + filePaiements.size());
    }
}