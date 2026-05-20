# Personne 5 - Paiement, promotions, wallet, historique et moyens sauvegardes

## Mission

Personne 5 gere le paiement simule de ChriOnline.

Cette partie ne contacte pas une vraie banque. Elle simule un paiement complet avec :

- type de paiement
- coupon
- frais de livraison
- wallet
- cashback
- score de fraude
- statut paiement
- recu
- historique
- moyen de paiement sauvegarde
- mise a jour de la commande en `PAYEE` ou `ANNULEE`

Le module principal est `ecommerce.personne5`. L'integration avec le serveur socket passe par `services.SocketPaymentService`.

## Parcours general

1. Le client choisit une commande a payer.
2. Le client envoie `SIMULATE_PAYMENT`.
3. `RequestRouter` appelle `SocketPaymentService`.
4. `SocketPaymentService` charge la commande serveur.
5. Il construit une commande compatible `ecommerce.personne5`.
6. `PaiementService` simule le paiement.
7. Le serveur sauvegarde l'historique.
8. Le statut de la commande devient `PAYEE` si succes, sinon `ANNULEE` ou erreur.

## Configuration

### `ecommerce.personne5.config.AppConfig`

Role : centralise les constantes generales du module paiement.

Utilisation :

- evite de disperser les valeurs fixes dans plusieurs classes.
- rend les demos plus faciles a modifier.

Bibliotheques :

- aucune bibliotheque externe.

### `ecommerce.personne5.config.BusinessRules`

Role : centralise les regles metier.

Exemples de regles possibles :

- seuils de fraude
- frais de livraison
- limites de montant
- taux de reduction
- taux de cashback

Pourquoi c'est important :

- Les regles paiement changent souvent.
- Les mettre dans une classe separee evite de modifier le moteur complet.

## Application de demonstration

### `ecommerce.personne5.main.PaiementApp`

Role : application console independante pour tester le module paiement sans JavaFX.

Methode importante :

- `main(String[] args)` : cree des objets de test, lance une simulation et affiche le resultat.

Utilisation :

- pratique pour presenter Personne 5 sans lancer tout le serveur.
- utile pour tester `PaiementService` rapidement.

Bibliotheques :

- Java console standard.

## Dashboard

### `ecommerce.personne5.dashboard.AdminDashboard`

Role : affiche un tableau de bord des statistiques paiement.

Methode importante :

- `afficherDashboard(PaiementStats stats)` : imprime les statistiques principales.

Donnees affichees :

- nombre total de paiements
- paiements acceptes
- paiements refuses
- paiements en attente
- remboursements
- chiffre d'affaires

## Modeles du package `ecommerce.personne5.model`

### `AuditLog`

Role : represente une action journalisee.

Donnees :

- `dateHeure` : date de l'action.
- `acteur` : utilisateur ou service responsable.
- `action` : type d'action.
- `details` : description.

Methodes importantes :

- `getDateHeure()`
- `getActeur()`
- `getAction()`
- `getDetails()`
- `toString()`

### `Commande`

Role : modele commande propre au module paiement Personne 5.

Attention :

- Ce n'est pas la meme classe que `models.Commande`.
- `models.Commande` est la commande serveur sauvegardee en base.
- `ecommerce.personne5.model.Commande` est une version adaptee a la simulation paiement.

Donnees :

- `idCommande`
- `dateCommande`
- `statut`
- `total`

Methodes importantes :

- `getIdCommande()`
- `getDateCommande()`
- `getStatut()`
- `getTotal()`
- `setStatut(StatutCommande statut)`
- `setTotal(double total)`
- `toString()`

### `Coupon`

Role : represente un code de reduction.

Donnees :

- `code` : nom du coupon.
- `reductionPourcentage` : pourcentage de reduction.
- `actif` : indique si le coupon est utilisable.

Methodes importantes :

- `getCode()`
- `getReductionPourcentage()`
- `isActif()`
- `toString()`

### `Paiement`

Role : objet central du paiement.

Donnees :

- `idPaiement`
- `montantInitial`
- `fraisLivraison`
- `reductionCoupon`
- `montantFinal`
- `datePaiement`
- `statut`
- `typePaiement`
- `idCommande`
- `message`
- `paiementSecurise`
- `tokenPaiement`
- `scoreFraude`

Methodes importantes :

- `Paiement(...)` : constructeur complet.
- `traiterPaiement()` : execute le traitement interne.
- `getIdPaiement()` : identifiant de simulation.
- `getMontantInitial()` : montant de depart.
- `getFraisLivraison()` : frais ajoutes.
- `getReductionCoupon()` : reduction appliquee.
- `getMontantFinal()` : montant final.
- `getDatePaiement()` : date.
- `getStatut()` : statut courant.
- `getTypePaiement()` : mode utilise.
- `getIdCommande()` : commande liee.
- `getMessage()` : resultat lisible.
- `isPaiementSecurise()` : indique si les controles securite sont valides.
- `getTokenPaiement()` : token de paiement.
- `getScoreFraude()` : score de risque.
- `setStatut(...)`, `setMessage(...)`, `setMontantFinal(...)` : mise a jour du resultat.
- `toString()` : affichage debug.

### `PaiementStats`

Role : statistiques globales.

Donnees :

- total paiements
- paiements acceptes
- paiements refuses
- paiements en attente
- paiements rembourses
- chiffre d'affaires

Methodes importantes :

- `getTotalPaiements()`
- `getPaiementsAcceptes()`
- `getPaiementsRefuses()`
- `getPaiementsEnAttente()`
- `getPaiementsRembourses()`
- `getChiffreAffaires()`
- `incrementerTotalPaiements()`
- `incrementerPaiementsAcceptes()`
- `incrementerPaiementsRefuses()`
- `incrementerPaiementsEnAttente()`
- `incrementerPaiementsRembourses()`
- `ajouterChiffreAffaires(double montant)`
- `toString()`

### `StatutCommande`

Role : enum des statuts de commande dans le module paiement.

Utilisation :

- evite les chaines libres.
- rend le code plus lisible.

### `StatutPaiement`

Role : enum des statuts de paiement.

Exemples :

- accepte
- refuse
- en attente
- rembourse

### `TypePaiement`

Role : enum des modes de paiement.

Exemples :

- carte bancaire
- PayPal
- wallet
- paiement a la livraison

### `Wallet`

Role : portefeuille virtuel.

Donnees :

- `solde`

Methodes importantes :

- `Wallet(double solde)` : cree un wallet avec solde initial.
- `getSolde()` : retourne le solde.
- `recharger(double montant)` : ajoute un montant.
- `payer(double montant)` : retire le montant si le solde suffit.
- `toString()` : affichage.

## Services du package `ecommerce.personne5.service`

### `PaiementService`

Role : moteur principal du paiement.

Etat interne :

- historique de paiements
- statistiques
- services de promotion, fraude, wallet, audit et file d'attente

Methodes importantes :

- `PaiementService()` : initialise les services.
- `getStats()` : retourne `PaiementStats`.
- `getAuditService()` : expose le service d'audit.
- `getQueueService()` : expose la file de paiements.
- `verifierCoupon(String code)` : cherche et valide un coupon.
- `calculerFraisLivraison(double montantApresReduction)` : applique les frais.
- `calculerMontantParTranche(Paiement paiement, int nombreTranches)` : paiement en plusieurs fois.
- `simulerPaiement(Commande commande, TypePaiement typePaiement, Coupon coupon, Wallet wallet)` : coeur du module.
- `afficherDetailsPaiement(Paiement paiement)` : detail console.
- `afficherRecu(Paiement paiement)` : recu console.
- `afficherHistorique()` : historique console.
- `afficherStats()` : statistiques console.
- `confirmerPaiement(Paiement paiement, Commande commande)` : valide la commande si paiement accepte.
- `rembourserPaiement(Paiement paiement, Commande commande)` : simule un remboursement.

### `AuditService`

Role : journalise les actions de paiement.

Methodes importantes :

- `enregistrerAction(String acteur, String action, String details)` : ajoute un log.
- `afficherLogs()` : affiche les logs.
- `sauvegarderLogsFichier()` : sauvegarde les logs dans un fichier.

Bibliotheques :

- Java Collections : liste de logs.
- Java IO : sauvegarde fichier.

### `CashbackService`

Role : calcule un cashback sur un montant.

Methodes importantes :

- `calculerCashback(double montant)` : retourne le cashback.
- `afficherCashback(double montant)` : affiche le cashback.

### `CurrencyService`

Role : conversions de devise pour affichage.

Methodes importantes :

- `madToUSD(double mad)` : convertit MAD vers USD.
- `madToEUR(double mad)` : convertit MAD vers EUR.
- `afficherConversions(double mad)` : affiche les conversions.

### `FlashSaleService`

Role : applique une reduction flash.

Methode importante :

- `appliquerFlashSale(double montant)` : retourne le montant reduit.

### `FraudDetectionService`

Role : calcule un score de fraude.

Methodes importantes :

- `calculerScoreFraude(double montant)` : donne un score.
- `transactionAutorisee(double montant, int seuilMax)` : refuse si le score depasse le seuil.

### `LoyaltyService`

Role : points de fidelite.

Methodes importantes :

- `calculerPoints(Commande commande)` : calcule les points selon la commande.
- `afficherPoints(int points)` : affiche les points.

### `PaiementQueueService`

Role : file d'attente des paiements.

Methodes importantes :

- `ajouterPaiement(Paiement paiement)` : ajoute un paiement.
- `traiterProchainPaiement()` : retire et traite le prochain.
- `afficherTailleFile()` : affiche la taille.

Bibliotheques :

- `Queue` / collections Java.

### `PaymentAnalyticsService`

Role : analyse les paiements.

Methodes importantes :

- `calculerRevenuTotal(List<Paiement> paiements)` : somme les revenus.
- `afficherAnalyse(List<Paiement> paiements)` : affiche une analyse.

### `PaymentRecommendationService`

Role : recommande un mode de paiement selon le montant.

Methode importante :

- `recommander(double montant)` : retourne une recommandation textuelle.

### `PaymentTimeoutService`

Role : detecte une transaction trop longue.

Methode importante :

- `verifierTimeout(long debutTransaction)` : compare le temps courant au debut.

### `PromotionService`

Role : applique des promotions.

Methodes importantes :

- `appliquerPromotion(double montant)` : applique une promotion generale.
- `verifierCoupon(String code, double montant)` : applique un coupon par code.

### `RetryPaymentService`

Role : relance un paiement echoue.

Methode importante :

- `retryPaiement(int tentativeMax)` : tente plusieurs fois.

### `RiskAnalysisService`

Role : autre calcul de risque.

Methode importante :

- `calculerScoreRisque(double montant)` : retourne un score de risque.

### `WalletService`

Role : operations sur un `Wallet`.

Methodes importantes :

- `rechargerWallet(Wallet wallet, double montant)` : ajoute de l'argent.
- `payerAvecWallet(Wallet wallet, double montant)` : paie si solde suffisant.
- `afficherSolde(Wallet wallet)` : affiche le solde.

## Utilitaires du package `ecommerce.personne5.utils`

### `IdGeneratorUtil`

Role : genere des identifiants.

Methodes importantes :

- `genererIdPaiement()` : ID paiement.
- `genererIdCommande()` : ID commande demo.

Bibliotheques :

- `UUID` ou generation aleatoire selon implementation.

### `LoggerUtil`

Role : affichage console standardise.

Methodes importantes :

- `info(String message)`
- `success(String message)`
- `warning(String message)`
- `error(String message)`

### `NotificationUtil`

Role : simule des notifications.

Methodes importantes :

- `envoyerNotificationSucces(String idCommande)`
- `envoyerNotificationEchec(String idCommande)`
- `envoyerNotificationRemboursement(String idCommande)`

### `PaiementSecurityUtil`

Role : controle de securite simplifie pour la simulation.

Methode importante :

- `verifierTransaction()` : retourne si la transaction est consideree comme sure.

### `PaiementValidator`

Role : valide les donnees avant paiement.

Methodes importantes :

- `commandeValide(Commande commande)` : commande non nulle et coherente.
- `montantValide(double montant)` : montant positif.
- `typePaiementValide(TypePaiement typePaiement)` : type non nul.
- `valider(Commande commande, double montant, TypePaiement typePaiement)` : validation globale.

### `RecuGenerator`

Role : genere un recu texte.

Methode importante :

- `genererRecu(Paiement paiement)` : retourne un recu lisible.

### `TokenUtil`

Role : genere un token de paiement.

Methode importante :

- `genererToken()` : token unique pour la simulation.

## Integration avec le serveur ChriOnline

### `services.SocketPaymentService`

Role : pont entre le protocole socket ChriOnline et le module Personne 5.

Methode importante :

- `simulatePayment(Message request)` : traite la commande `SIMULATE_PAYMENT`.

Etapes internes :

1. Lit le payload JSON.
2. Recupere `commandeId`, `userId`, type de paiement et coupon.
3. Charge la commande avec `CommandeService.getCommandeById`.
4. Verifie que la commande appartient au bon utilisateur.
5. Verifie que la commande est payable.
6. Convertit `models.Commande` vers `ecommerce.personne5.model.Commande`.
7. Appelle `PaiementService.simulerPaiement`.
8. Sauvegarde l'historique avec `PaymentHistoryDAO`.
9. Met a jour le statut de commande.
10. Sauvegarde le moyen de paiement si demande.

Methodes internes typiques :

- `parseType(...)` : convertit le texte en `TypePaiement`.
- `parseBool(...)` : lit un booleen.
- `toJsonPayload(...)` : construit la reponse.
- `maybeSavePaymentTemplate(...)` : sauvegarde un moyen masque.
- `escJson(...)` : echappe une chaine JSON.
- `err(...)` : construit une erreur.

### `persistence.PaymentHistoryDAO`

Role : sauvegarde une ligne d'historique paiement.

Methode importante :

- `insert(commandeId, userId, typePaiement, idPaiementSimule, statut, montantFinal, messageResume)` : insere dans la table d'historique.

Bibliotheques :

- JDBC.
- MySQL Connector/J.

### `persistence.SavedPaymentMethodDAO`

Role : gere les moyens de paiement sauvegardes.

Record :

- `SavedRow` : contient `id`, `typePaiement`, `displayLabel`, `createdAtIso`.

Methodes importantes :

- `insert(int userId, String typePaiement, String displayLabel, String templateJson)` : sauvegarde un moyen masque.
- `listByUser(int userId)` : liste les moyens d'un utilisateur.
- `deleteForUser(int methodId, int userId)` : supprime un moyen si l'utilisateur en est proprietaire.
- `toJsonArray(List<SavedRow> rows)` : transforme la liste en JSON.

### `services.SavedPaymentService`

Role : expose les moyens sauvegardes au client JavaFX.

Methodes importantes :

- `list(Message request)` : traite `LIST_SAVED_PAYMENT_METHODS`.
- `delete(Message request)` : traite `DELETE_SAVED_PAYMENT_METHOD`.

### `services.StorageCryptoService`

Role : chiffre/dechiffre certaines donnees stockees si l'option est activee.

Methodes importantes :

- `reloadConfig()` : recharge la configuration.
- `sealIfEnabled(String plaintext)` : chiffre avant stockage si active.
- `openIfNeeded(String stored)` : dechiffre a la lecture si necessaire.

## Bibliotheques globales

- Java Collections : listes, historiques, files.
- Java Time : dates de paiement.
- Java UUID/Random : tokens et identifiants.
- Java JDBC : sauvegarde historique et moyens.
- MySQL Connector/J : pilote SQL.
- Java IO : logs fichier.
- Crypto Java : stockage chiffre optionnel via `StorageCryptoService`.

## Checklist de test

1. Creer une commande.
2. Lancer un paiement carte.
3. Tester un coupon.
4. Tester un paiement refuse.
5. Verifier que la commande devient `PAYEE` si succes.
6. Verifier l'historique paiement.
7. Sauvegarder un moyen de paiement.
8. Lister les moyens sauvegardes.
9. Supprimer un moyen sauvegarde.
