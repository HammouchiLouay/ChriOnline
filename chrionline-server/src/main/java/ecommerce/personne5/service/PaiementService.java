package ecommerce.personne5.service;

import ecommerce.personne5.config.BusinessRules;
import ecommerce.personne5.model.Commande;
import ecommerce.personne5.model.Coupon;
import ecommerce.personne5.model.Paiement;
import ecommerce.personne5.model.PaiementStats;
import ecommerce.personne5.model.StatutCommande;
import ecommerce.personne5.model.StatutPaiement;
import ecommerce.personne5.model.TypePaiement;
import ecommerce.personne5.model.Wallet;
import ecommerce.personne5.utils.IdGeneratorUtil;
import ecommerce.personne5.utils.LoggerUtil;
import ecommerce.personne5.utils.NotificationUtil;
import ecommerce.personne5.utils.PaiementSecurityUtil;
import ecommerce.personne5.utils.RecuGenerator;
import ecommerce.personne5.utils.TokenUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cœur métier du module paiement (personne 5) : simulation, coupons, frais de livraison, file, détection de fraude,
 * portefeuille et audit. Consommé par la console {@link ecommerce.personne5.main.PaiementApp} et par
 * {@link services.SocketPaymentService}.
 */
public class PaiementService {

    private final PromotionService promotionService;
    private final PaiementStats stats;
    private final AuditService auditService;
    private final PaiementQueueService queueService;
    private final FraudDetectionService fraudDetectionService;
    private final WalletService walletService;
    private final List<Paiement> historiquePaiements;

    public PaiementService() {
        this.promotionService = new PromotionService();
        this.stats = new PaiementStats();
        this.auditService = new AuditService();
        this.queueService = new PaiementQueueService();
        this.fraudDetectionService = new FraudDetectionService();
        this.walletService = new WalletService();
        this.historiquePaiements = new ArrayList<>();
    }

    /** Statistiques agrégées des paiements simulés. */
    public PaiementStats getStats() {
        return stats;
    }

    /** Journal d’audit des actions. */
    public AuditService getAuditService() {
        return auditService;
    }

    /** File de traitement des paiements. */
    public PaiementQueueService getQueueService() {
        return queueService;
    }

    /** Valide un code coupon connu (ex. PROMO10) ou retourne {@code null}. */
    public Coupon verifierCoupon(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }

        code = code.trim().toUpperCase();

        switch (code) {
            case "PROMO10":
                return new Coupon("PROMO10", 10.0, true);
            case "PROMO20":
                return new Coupon("PROMO20", 20.0, true);
            case "PROMO30":
                return new Coupon("PROMO30", 30.0, true);
            default:
                return null;
        }
    }

    /** Frais standard ou gratuit selon le seuil défini dans {@link ecommerce.personne5.config.BusinessRules}. */
    public double calculerFraisLivraison(double montantApresReduction) {
        if (montantApresReduction >= BusinessRules.LIVRAISON_GRATUITE_A_PARTIR) {
            return 0.0;
        }
        return BusinessRules.FRAIS_LIVRAISON_STANDARD;
    }

    /** Répartit le montant final en paiements fractionnés (2x, 3x). */
    public double calculerMontantParTranche(Paiement paiement, int nombreTranches) {
        if (paiement == null || nombreTranches <= 0) {
            return 0.0;
        }
        return paiement.getMontantFinal() / nombreTranches;
    }

    /**
     * Simule un paiement : promotions, coupon, livraison, score anti-fraude et règles par {@link TypePaiement}.
     *
     * @return objet {@link Paiement} ou {@code null} si commande absente
     */
    public Paiement simulerPaiement(Commande commande,
                                    TypePaiement typePaiement,
                                    Coupon coupon,
                                    Wallet wallet) {

        if (commande == null) {
            LoggerUtil.error("Commande introuvable.");
            return null;
        }

        double montantInitial = commande.getTotal();

        double montantApresPromo = promotionService.appliquerPromotion(montantInitial);

        double montantApresCoupon = montantApresPromo;
        double reductionCoupon = 0.0;

        if (coupon != null && coupon.isActif()) {
            montantApresCoupon = montantApresPromo * (1 - coupon.getReductionPourcentage() / 100.0);
            reductionCoupon = montantApresPromo - montantApresCoupon;
        }

        double fraisLivraison = calculerFraisLivraison(montantApresCoupon);
        double montantFinal = montantApresCoupon + fraisLivraison;

        boolean paiementSecurise = PaiementSecurityUtil.verifierTransaction();
        String tokenPaiement = TokenUtil.genererToken();
        int scoreFraude = fraudDetectionService.calculerScoreFraude(montantFinal);

        StatutPaiement statut = StatutPaiement.EN_ATTENTE;
        String message = "Paiement en attente.";

        switch (typePaiement) {

            case WALLET:
                if (wallet == null) {
                    statut = StatutPaiement.REFUSE;
                    message = "Wallet introuvable.";
                } else if (walletService.payerAvecWallet(wallet, montantFinal)) {
                    statut = StatutPaiement.ACCEPTE;
                    message = "Paiement wallet accepte.";
                } else {
                    statut = StatutPaiement.REFUSE;
                    message = "Solde wallet insuffisant.";
                }
                break;

            case PAIEMENT_2X:
                if (montantFinal < BusinessRules.MONTANT_MIN_PAIEMENT_2X) {
                    statut = StatutPaiement.REFUSE;
                    message = "Montant insuffisant pour paiement en 2 fois.";
                } else if (scoreFraude > BusinessRules.SCORE_FRAUDE_MAX) {
                    statut = StatutPaiement.REFUSE;
                    message = "Paiement refuse : risque de fraude eleve.";
                } else {
                    statut = StatutPaiement.ACCEPTE;
                    message = "Paiement en 2 fois accepte.";
                }
                break;

            case PAIEMENT_3X:
                if (montantFinal < BusinessRules.MONTANT_MIN_PAIEMENT_3X) {
                    statut = StatutPaiement.REFUSE;
                    message = "Montant insuffisant pour paiement en 3 fois.";
                } else if (scoreFraude > BusinessRules.SCORE_FRAUDE_MAX) {
                    statut = StatutPaiement.REFUSE;
                    message = "Paiement refuse : risque de fraude eleve.";
                } else {
                    statut = StatutPaiement.ACCEPTE;
                    message = "Paiement en 3 fois accepte.";
                }
                break;

            case A_LA_LIVRAISON:
                statut = StatutPaiement.ACCEPTE;
                message = "Commande validee avec paiement a la livraison.";
                break;

            case CARTE_BANCAIRE:
            case PAYPAL:
            case STRIPE:
                if (!paiementSecurise) {
                    statut = StatutPaiement.REFUSE;
                    message = "Transaction non securisee.";
                } else if (scoreFraude > BusinessRules.SCORE_FRAUDE_MAX) {
                    statut = StatutPaiement.REFUSE;
                    message = "Paiement refuse : score de fraude trop eleve.";
                } else {
                    statut = StatutPaiement.ACCEPTE;
                    message = "Paiement electronique accepte.";
                }
                break;

            default:
                statut = StatutPaiement.REFUSE;
                message = "Type de paiement non pris en charge.";
                break;
        }

        Paiement paiement = new Paiement(
                IdGeneratorUtil.genererIdPaiement(),
                montantInitial,
                fraisLivraison,
                reductionCoupon,
                montantFinal,
                LocalDateTime.now().toString(),
                statut,
                typePaiement,
                commande.getIdCommande(),
                message,
                paiementSecurise,
                tokenPaiement,
                scoreFraude
        );

        historiquePaiements.add(paiement);
        queueService.ajouterPaiement(paiement);

        stats.incrementerTotalPaiements();

        if (statut == StatutPaiement.ACCEPTE) {
            stats.incrementerPaiementsAcceptes();
            stats.ajouterChiffreAffaires(montantFinal);
        } else if (statut == StatutPaiement.REFUSE) {
            stats.incrementerPaiementsRefuses();
        } else {
            stats.incrementerPaiementsEnAttente();
        }

        auditService.enregistrerAction(
                "SYSTEME",
                "SIMULATION_PAIEMENT",
                "Paiement " + paiement.getIdPaiement() + " - " + paiement.getMessage()
        );

        LoggerUtil.info("Paiement cree : " + paiement.getIdPaiement());

        return paiement;
    }

    /** Affiche le détail d’un paiement sur la console (démo). */
    public void afficherDetailsPaiement(Paiement paiement) {
        if (paiement == null) {
            LoggerUtil.warning("Aucun paiement a afficher.");
            return;
        }

        System.out.println("\n===== DETAILS DU PAIEMENT =====");
        System.out.println("ID paiement      : " + paiement.getIdPaiement());
        System.out.println("ID commande      : " + paiement.getIdCommande());
        System.out.println("Montant initial  : " + String.format("%.2f MAD", paiement.getMontantInitial()));
        System.out.println("Frais livraison  : " + String.format("%.2f MAD", paiement.getFraisLivraison()));
        System.out.println("Reduction coupon : -" + String.format("%.2f MAD", paiement.getReductionCoupon()));
        System.out.println("Montant final    : " + String.format("%.2f MAD", paiement.getMontantFinal()));
        System.out.println("Type paiement    : " + paiement.getTypePaiement());
        System.out.println("Statut           : " + paiement.getStatut());
        System.out.println("Score fraude     : " + paiement.getScoreFraude() + "%");
        System.out.println("Message          : " + paiement.getMessage());
        System.out.println("================================\n");
    }

    /** Affiche un reçu texte via {@link ecommerce.personne5.utils.RecuGenerator}. */
    public void afficherRecu(Paiement paiement) {
        if (paiement == null) {
            LoggerUtil.warning("Impossible d'afficher le recu.");
            return;
        }

        System.out.println(RecuGenerator.genererRecu(paiement));
    }

    /** Liste l’historique des paiements simulés en mémoire. */
    public void afficherHistorique() {
        System.out.println("\n===== HISTORIQUE DES PAIEMENTS =====");

        if (historiquePaiements.isEmpty()) {
            System.out.println("Aucun paiement enregistre.");
        } else {
            for (Paiement paiement : historiquePaiements) {
                System.out.println(paiement);
            }
        }

        System.out.println("====================================\n");
    }

    /** Affiche les statistiques agrégées. */
    public void afficherStats() {
        System.out.println(stats);
    }

    /** Met à jour le statut de la commande selon l’issue du paiement et envoie notifications / audit. */
    public void confirmerPaiement(Paiement paiement, Commande commande) {
        if (paiement == null || commande == null) {
            LoggerUtil.warning("Confirmation impossible.");
            return;
        }

        if (paiement.getStatut() == StatutPaiement.ACCEPTE) {
            if (paiement.getTypePaiement() == TypePaiement.A_LA_LIVRAISON) {
                commande.setStatut(StatutCommande.VALIDEE);
            } else {
                commande.setStatut(StatutCommande.PAYEE);
            }

            auditService.enregistrerAction(
                    "SYSTEME",
                    "CONFIRMATION_PAIEMENT",
                    "Commande " + commande.getIdCommande() + " confirmee."
            );

            NotificationUtil.envoyerNotificationSucces(commande.getIdCommande());
            LoggerUtil.success("Paiement confirme avec succes.");
        } else {
            commande.setStatut(StatutCommande.ANNULEE);

            auditService.enregistrerAction(
                    "SYSTEME",
                    "ECHEC_PAIEMENT",
                    "Commande " + commande.getIdCommande() + " refusee."
            );

            NotificationUtil.envoyerNotificationEchec(commande.getIdCommande());
            LoggerUtil.error("Le paiement a echoue.");
        }
    }

    /** Passe le paiement et la commande en état remboursé si le paiement était accepté. */
    public boolean rembourserPaiement(Paiement paiement, Commande commande) {
        if (paiement == null || commande == null) {
            LoggerUtil.warning("Remboursement impossible.");
            return false;
        }

        if (paiement.getStatut() != StatutPaiement.ACCEPTE) {
            LoggerUtil.warning("Seul un paiement accepte peut etre rembourse.");
            return false;
        }

        paiement.setStatut(StatutPaiement.REMBOURSE);
        paiement.setMessage("Paiement rembourse avec succes.");

        commande.setStatut(StatutCommande.REMBOURSEE);

        stats.incrementerPaiementsRembourses();

        auditService.enregistrerAction(
                "ADMIN",
                "REMBOURSEMENT",
                "Remboursement du paiement " + paiement.getIdPaiement()
        );

        NotificationUtil.envoyerNotificationRemboursement(commande.getIdCommande());
        LoggerUtil.success("Remboursement effectue.");

        return true;
    }
}