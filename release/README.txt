MineGen 1.0.0
Generateur de terrain autonome — Paper 1.21.11 / Java 21

INSTALLATION
1. Arretez votre serveur Paper 1.21.11.
2. Placez MineGen-1.0.0.jar dans plugins/ (un seul JAR MineGen).
3. Demarrez le serveur. MineGen cree plugins/MineGen/config.yml et un monde
   nomme minegen. Aucun autre plugin ni modification de bukkit.yml n'est requis.
4. En tant qu'operateur, utilisez /minegen tp pour le visiter.

Le monde principal existant n'est pas remplace. Les joueurs habituels gardent
leur emplacement. world.first-join: true envoie les nouveaux joueurs dans
MineGen lors de leur premiere connexion au serveur.

COMMANDES ET PERMISSIONS
/minegen status : affiche l'etat du monde.
/minegen tp     : rejoint le spawn du monde genere.
/mg            : alias de /minegen.
Les sous-commandes disposent de la completion.
minegen.teleport : autorise /minegen tp ; reserve aux operateurs par defaut.
Avec LuckPerms, pour autoriser tous les joueurs :
  lp group default permission set minegen.teleport true
LuckPerms est facultatif. Il n'est pas inclus dans cette distribution.

CONFIGURATION (serveur arrete, puis redemarrage)
world.enabled : active ou desactive le chargement automatique du monde.
world.name : nom du monde ; lettres, chiffres, tiret et underscore uniquement.
world.seed : texte ou nombre entre guillemets. Vide = graine aleatoire sauvegardee.
world.structures : structures vanilla pour les nouveaux mondes.
world.difficulty : PEACEFUL, EASY, NORMAL ou HARD.
world.first-join : teleporte les nouveaux joueurs, false par defaut.
performance.fast-biomes : active l'optimisation du calcul des biomes.

La graine et les structures doivent etre choisies AVANT la creation d'un monde.
Le fichier level.dat reste la reference pour un monde deja genere. Changer sa
graine dans config.yml ne transforme pas le terrain existant. Pour une autre
graine, choisissez un nouveau world.name libre et conservez votre ancien monde.
Le bloc internal est genere automatiquement : ne le modifiez pas.
Si un dossier portant le nom demande existe deja sans etre reconnu comme le
monde gere, MineGen refuse de l'ouvrir et explique le probleme dans la console.
Ne supprimez pas la configuration en conservant le monde : le controle de
propriete refusera de reprendre ce dossier sans son historique.

GENERATION ET COMPATIBILITE
Terrain, surface, cavernes et socle : moteur MineGen.
Decorations, minerais, structures et apparitions : mecanismes du serveur.
Le moteur est celui employe par Eternium ; cette edition ajoute l'installation
autonome sans changer son algorithme de terrain.
Cette version cible exclusivement Paper 1.21.11. Spigot, Folia et les autres
versions Minecraft ne sont pas annonces compatibles. Aucun benchmark lourd
n'est lance automatiquement. Aucune pregeneration du monde complet n'est lancee.
Les optimisations internes dependent de Paper ; en cas d'indisponibilite, le
moteur dispose d'un chemin Bukkit de secours et signale la situation en console.

ENTRETIEN
Sauvegardez les mondes et la configuration avant une mise a jour du generateur.
Utilisez un arret/redemarrage complet, pas /reload ni un chargeur de plugins.
Le JAR n'inclut ni serveur, ni monde Eternium, ni donnees de joueurs.
Consultez VALIDATION.txt pour les essais effectues sur cette version.
