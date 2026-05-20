package ecommerce.personne5.main;

import ecommerce.personne5.dashboard.AdminDashboard;
import ecommerce.personne5.model.Commande;
import ecommerce.personne5.model.Coupon;
import ecommerce.personne5.model.Paiement;
import ecommerce.personne5.model.StatutCommande;
import ecommerce.personne5.model.TypePaiement;
import ecommerce.personne5.model.Wallet;
import ecommerce.personne5.service.CurrencyService;
import ecommerce.personne5.service.PaiementService;
import ecommerce.personne5.service.WalletService;
import ecommerce.personne5.utils.IdGeneratorUtil;
import ecommerce.personne5.utils.LoggerUtil;

import java.time.LocalDateTime;
import java.util.Scanner;

/**
 * Démonstration console du module paiement (personne 5) : menu interactif autour de {@link PaiementService}.
 */
public class PaiementApp {

    private static Paiement dernierPaiement = null;

    /** Point d’entrée : boucle de menu et scénarios de paiement simulés. */
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        PaiementService paiementService = new PaiementService();
        WalletService walletService = new WalletService();
        CurrencyService currencyService = new CurrencyService();

        Wallet wallet = new Wallet(300.00);

        Commande commande = new Commande(
                IdGeneratorUtil.genererIdCommande(),
                LocalDateTime.now().toString(),
                StatutCommande.EN_ATTENTE,
                799.99
        );

        int choix;
        do {
            afficherMenu();
            System.out.print("Votre choix : ");

            while (!scanner.hasNextInt()) {
                System.out.print("Veuillez entrer un nombre valide : ");
                scanner.next();
            }

            choix = scanner.nextInt();
            scanner.nextLine();

            switch (choix) {
                case 1:
                    dernierPaiement = traiterPaiementCommande(scanner, paiementService, commande, wallet);
                    break;
                case 2:
                    paiementService.afficherHistorique();
                    break;
                case 3:
                    System.out.println(commande);
                    break;
                case 4:
                    paiementService.afficherStats();
                    break;
                case 5:
                    rembourserDernierPaiement(paiementService, commande);
                    break;
                case 6:
                    afficherPaiementFractionne(paiementService);
                    break;
                case 7:
                    walletService.afficherSolde(wallet);
                    break;
                case 8:
                    rechargerWallet(scanner, walletService, wallet);
                    break;
                case 9:
                    afficherConversions(currencyService);
                    break;
                case 10:
                    AdminDashboard.afficherDashboard(paiementService.getStats());
                    break;
                case 11:
                    paiementService.getAuditService().afficherLogs();
                    break;
                case 12:
                    paiementService.getQueueService().afficherTailleFile();
                    paiementService.getQueueService().traiterProchainPaiement();
                    break;
                case 13:
                    LoggerUtil.info("Fermeture de l'application...");
                    paiementService.getAuditService().sauvegarderLogsFichier();
                    break;
                default:
                    LoggerUtil.warning("Choix invalide.");
            }

        } while (choix != 13);

        scanner.close();
    }

    private static void afficherMenu() {
        System.out.println("========= SYSTEME DE PAIEMENT =========");
        System.out.println("1  - Payer la commande");
        System.out.println("2  - Voir l'historique des paiements");
        System.out.println("3  - Voir l'etat de la commande");
        System.out.println("4  - Voir les statistiques");
        System.out.println("5  - Rembourser le dernier paiement");
        System.out.println("6  - Voir montant par tranche (2x / 3x)");
        System.out.println("7  - Voir solde wallet");
        System.out.println("8  - Recharger wallet");
        System.out.println("9  - Voir conversion devise");
        System.out.println("10 - Voir dashboard admin");
        System.out.println("11 - Voir audit logs");
        System.out.println("12 - Traiter file des paiements");
        System.out.println("13 - Quitter");
        System.out.println("======================================");
    }

    private static Paiement traiterPaiementCommande(Scanner scanner,
                                                    PaiementService paiementService,
                                                    Commande commande,
                                                    Wallet wallet) {
        if (commande.getStatut() == StatutCommande.PAYEE) {
            LoggerUtil.warning("Cette commande est deja payee.");
            return null;
        }

        TypePaiement typePaiement = choisirTypePaiement(scanner);
        if (typePaiement == null) {
            LoggerUtil.error("Type de paiement invalide.");
            return null;
        }

        Coupon coupon = null;
        System.out.print("Voulez-vous entrer un code promo ? (oui/non) : ");
        String reponseCoupon = scanner.nextLine();

        if ("oui".equalsIgnoreCase(reponseCoupon)) {
            System.out.print("Entrez le code promo : ");
            String code = scanner.nextLine();
            coupon = paiementService.verifierCoupon(code);

            if (coupon == null) {
                LoggerUtil.warning("Coupon invalide ou inexistant.");
            }
        }

        Paiement paiement = paiementService.simulerPaiement(commande, typePaiement, coupon, wallet);
        paiementService.afficherDetailsPaiement(paiement);
        paiementService.afficherRecu(paiement);
        paiementService.confirmerPaiement(paiement, commande);

        return paiement;
    }

    private static void rembourserDernierPaiement(PaiementService paiementService, Commande commande) {
        if (dernierPaiement == null) {
            LoggerUtil.warning("Aucun paiement a rembourser.");
            return;
        }

        boolean resultat = paiementService.rembourserPaiement(dernierPaiement, commande);
        if (!resultat) {
            LoggerUtil.warning("Le remboursement n'a pas ete effectue.");
        }
    }

    private static void afficherPaiementFractionne(PaiementService paiementService) {
        if (dernierPaiement == null) {
            LoggerUtil.warning("Aucun paiement disponible.");
            return;
        }

        System.out.println("\n===== PAIEMENT FRACTIONNE =====");
        System.out.println("Montant final : " + String.format("%.2f MAD", dernierPaiement.getMontantFinal()));
        System.out.println("En 2 fois     : " + String.format("%.2f MAD", paiementService.calculerMontantParTranche(dernierPaiement, 2)));
        System.out.println("En 3 fois     : " + String.format("%.2f MAD", paiementService.calculerMontantParTranche(dernierPaiement, 3)));
        System.out.println("================================\n");
    }

    private static void rechargerWallet(Scanner scanner, WalletService walletService, Wallet wallet) {
        System.out.print("Montant a recharger : ");
        while (!scanner.hasNextDouble()) {
            System.out.print("Veuillez entrer un nombre valide : ");
            scanner.next();
        }
        double montant = scanner.nextDouble();
        scanner.nextLine();
        walletService.rechargerWallet(wallet, montant);
    }

    private static void afficherConversions(CurrencyService currencyService) {
        if (dernierPaiement == null) {
            LoggerUtil.warning("Aucun paiement disponible pour conversion.");
            return;
        }
        currencyService.afficherConversions(dernierPaiement.getMontantFinal());
    }

    private static TypePaiement choisirTypePaiement(Scanner scanner) {
        System.out.println("\nChoisissez un type de paiement :");
        System.out.println("1 - Carte bancaire");
        System.out.println("2 - PayPal");
        System.out.println("3 - Stripe");
        System.out.println("4 - A la livraison");
        System.out.println("5 - Wallet");
        System.out.println("6 - Paiement en 2 fois");
        System.out.println("7 - Paiement en 3 fois");
        System.out.print("Choix : ");

        while (!scanner.hasNextInt()) {
            System.out.print("Veuillez entrer un nombre valide : ");
            scanner.next();
        }

        int choixPaiement = scanner.nextInt();
        scanner.nextLine();

        switch (choixPaiement) {
            case 1:
                return TypePaiement.CARTE_BANCAIRE;
            case 2:
                return TypePaiement.PAYPAL;
            case 3:
                return TypePaiement.STRIPE;
            case 4:
                return TypePaiement.A_LA_LIVRAISON;
            case 5:
                return TypePaiement.WALLET;
            case 6:
                return TypePaiement.PAIEMENT_2X;
            case 7:
                return TypePaiement.PAIEMENT_3X;
            default:
                return null;
        }
    }
}