# SQL — ChriOnline

## Fichiers principaux

1. **`chrionline_create_tables.sql`** — Crée la base `chrionline`, **toutes les tables** (`user`, `products`, `commande`, `ligne_commande`, `historique_paiement`, `methode_paiement_enregistree`) et insère l’utilisateur **démo** (`demo@chrionline.local` / `demo123`). **Aucune ligne produit.**

2. **`chrionline_products_data.sql`** — `USE chrionline;` puis **INSERT** des **100 produits** (généré depuis le classeur Excel via `tools/excel_to_products_sql.py`).

### Ordre d’exécution (MySQL)

```bash
mysql -u root -p < sql/chrionline_create_tables.sql
mysql -u root -p < sql/chrionline_products_data.sql
```

Ou en une session :

```sql
SOURCE /chemin/vers/chrionline_create_tables.sql;
SOURCE /chemin/vers/chrionline_products_data.sql;
```

## Régénérer le fichier produits

Si le fichier `.xlsx` du catalogue est présent (voir chemin par défaut dans `tools/excel_to_products_sql.py`) :

```bash
python tools/excel_to_products_sql.py > sql/chrionline_products_data.sql
```

Puis rajouter en tête du fichier généré les lignes `USE chrionline;` et le commentaire d’en-tête (ou les conserver si vous éditez le script pour les inclure).

Les colonnes doivent correspondre à la table `products` définie dans `chrionline_create_tables.sql`.
