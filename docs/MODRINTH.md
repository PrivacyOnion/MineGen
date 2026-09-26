# Fiche Modrinth prête à copier

## Réglages

- Nom : **MineGen**
- Slug souhaité : `minegen` (disponibilité à vérifier)
- Type : **Plugin**
- Plateforme / loader : **Paper**
- Version Minecraft : **1.21.11** uniquement
- Java : **21**
- Environnement serveur : **Required**
- Environnement client : **Unsupported** (aucune installation côté joueur)
- Catégorie principale : **World Generation** (`worldgen`)
- Catégorie secondaire facultative : **Optimization** (`optimization`), pour mettre en avant les optimisations internes ; aucun gain chiffré validé n'est annoncé.
- Ne pas sélectionner Fabric, Forge, NeoForge, Spigot ou Folia.
- Version : **1.0.0**, type **Release**
- Fichier : `release/dist/MineGen-1.0.0.jar` (pas le ZIP)
- Sources : https://github.com/PrivacyOnion/MineGen
- Bugs : https://github.com/PrivacyOnion/MineGen/issues
- Licence : aucune licence ouverte choisie. Ne pas sélectionner MIT/GPL sans décision du créateur ; choisir les droits réservés si cela correspond à ses droits sur le contenu.

## Résumé court français

Générateur de terrain autonome pour Paper 1.21.11 : monde dédié, graine configurable et installation simple, sans mod client.

## English summary

Standalone terrain generation for Paper 1.21.11, with a dedicated world, configurable seed and simple setup. No client mod needed.

## Description française

**MineGen crée un monde dédié avec le moteur de terrain utilisé par Eternium.** Installe le plugin sur ton serveur Paper, démarre-le, puis explore avec `/minegen tp`.

### Fonctionnalités

- Génération du terrain, des surfaces, des cavernes et du socle par MineGen.
- Décorations, minerais, structures et apparitions gérés par le serveur.
- Création et chargement automatiques d'un monde séparé.
- Nom du monde, graine, structures et difficulté configurables.
- Téléportation facultative des nouveaux joueurs à leur première connexion.
- Optimisations internes pour Paper, avec une voie de secours Bukkit.

Le monde principal existant est conservé. Aucun autre plugin ni mod client n'est nécessaire.

### Installation

1. Arrête ton serveur **Paper 1.21.11 / Java 21**.
2. Place **MineGen-1.0.0.jar** dans le dossier `plugins/`.
3. Démarre le serveur : MineGen crée sa configuration et le monde `minegen`.
4. Utilise **`/minegen tp`** en tant qu'opérateur pour le visiter.

### Commandes et permission

- `/minegen status` : affiche l'état du monde.
- `/minegen tp` : rejoint le spawn du monde.
- `/mg` : alias de `/minegen`.
- `minegen.teleport` : autorise la téléportation ; réservé aux opérateurs par défaut.

### Configuration et compatibilité

Modifie `plugins/MineGen/config.yml` serveur arrêté, puis redémarre. Choisis la graine avant la création du monde. Pour changer de graine, utilise un nouveau nom de monde libre. Conserve la configuration et ses données internes avec le monde.

Cette version cible **Paper 1.21.11 uniquement**. Spigot, Folia et les autres versions de Minecraft ne sont pas annoncés compatibles. Sauvegarde tes mondes avant une mise à jour et utilise un redémarrage complet.

Les essais d'installation et de téléportation sont décrits dans le rapport de validation du dépôt GitHub. Aucun gain de performance chiffré n'est garanti.

### Crédits

**JMS — créateur du projet.**

## Transparence et icône

Déclarer l'assistance IA à la description/publication dans Content Disclosures ; décrire aussi l'usage réel d'IA dans le code si applicable. L'historique du développement n'a pas été audité ici.

Le SVG et le PNG du dossier `assets/` ont été dessinés par code avec assistance IA pour GitHub. Leur conversion en PNG ne garantit PAS leur admissibilité sur Modrinth, dont la règle couvre les images créées ou dérivées de sorties d'IA. Pour Modrinth, utiliser un dessin réalisé par JMS ou une vraie capture en jeu dont il détient les droits, ou publier sans icône.

Règles vérifiées le 26 septembre 2026 :
https://support.modrinth.com/en/articles/16551575-disclosure-and-usage-of-ai
https://modrinth.com/legal/rules
