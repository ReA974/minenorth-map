# MineNorth Carte (minenorth_map) — Forge 1.20.1

Mod à installer **côté serveur ET côté client**.

## Joueurs
- **M** : carte plein écran (glisser = déplacer, molette = zoom, Espace = recentrer)
- **N** : afficher / masquer la minicarte
- **+ / - du pavé numérique** : zoom de la minicarte
- Bouton **Points : affichés/cachés** (en haut à droite de la carte) : masque les points du serveur
  sur la carte et la minicarte (choix propre à chaque joueur ; le point guidé reste visible)
- **Clic droit** sur la carte : pose un **repère perso temporaire** (visible par soi seul, 5 max,
  guidage automatique). Clic droit dessus (ou dans la liste) pour le retirer. Il disparaît à l'arrivée,
  à la déconnexion ou après 60 min (`dureeReperesPerso` dans la config client)
- Clic sur un point (carte ou liste de gauche) : active/désactive le **guidage**
  (flèche + distance en haut de l'écran, s'arrête à l'arrivée)
- La carte est **commune** : c'est le serveur qui la dessine et l'envoie à tout le monde
  (un nouveau joueur voit directement toute la carte déjà dessinée). Cache local : `.minecraft/minenorth_map/`.
- Le serveur dessine automatiquement chaque chunk chargé par n'importe quel joueur, et redessine
  les chunks où des blocs sont posés/cassés.
- **Aucun joueur n'est affiché**, seulement le terrain, les points de repère et sa propre flèche.

## Staff : voir les joueurs
Sur la grande carte (M), un bouton **Joueurs : ON/OFF** apparaît en haut à droite pour le staff autorisé :
- permission **« Carte : voir joueurs »** du panneau admin (donnée automatiquement aux rôles qui ont « Gérer le staff », donc le Gérant),
- ou op du niveau `niveauOpVoirJoueurs` (4 par défaut, 5 = désactivé) dans `minenorth_map-server.toml`.
Les positions ne sont envoyées qu'aux personnes autorisées, uniquement pendant que la carte est ouverte. La minicarte n'affiche jamais les joueurs.

## OP (permission niveau 2)
Noms avec espaces => entre guillemets.
```
/carte point ajouter "<nom>" [type]              à ta position (type par défaut : autre)
/carte point placer "<nom>" <x> <y> <z> [type]
/carte point deplacer "<nom>"                    déplace à ta position
/carte point supprimer "<nom>"
/carte point renommer "<ancien>" "<nouveau>"
/carte point type "<nom>" <type>
/carte point types                               liste des types
/carte point lier "<nom>" "<entreprise>"         affiche Ouvert / Fermé sur la carte
/carte point delier "<nom>"
/carte point liste                               (avec [TP] cliquable)
/carte point tp "<nom>"
```
Dessiner toute la map d'un coup (seuls les chunks déjà générés, aucun nouveau terrain créé) :
```
/carte generer rayon <blocs>                     autour de toi (ex : /carte generer rayon 3000)
/carte generer zone <x1> <z1> <x2> <z2>
/carte generer statut
/carte generer stop
```
Carte serveur stockée dans `<monde>/minenorth_map/`. Réglages : `<monde>/serverconfig/minenorth_map-server.toml`.

Types de points : `entreprise, administration, police, secours, sante, commerce, transport, loisir, logement, autre`.
Chaque type a une couleur fixe ; la liste se modifie dans `<monde>/serverconfig/minenorth_map-server.toml` (`types = ["id;Libellé;RRGGBB"]`).
Les points existants (anciennement par couleur) passent en type `autre` : à reclasser avec `/carte point type`.

**Entreprises ouvertes / fermées** : un point lié à une entreprise (`/carte point lier`) affiche une pastille verte « Ouvert » ou rouge « Fermé ».
Ouvert = le PDG (ou un grade « gérer ») a appuyé sur *Ouvrir l'entreprise* dans la tablette ET au moins un membre est connecté.
Nécessite le mod Entreprises (sinon les points restent normaux).

**Édition depuis la carte (OP niveau 2)** : bouton *Édition : OFF/ON* sur la grande carte (visible seulement pour les OP).
En mode édition : clic sur un point = formulaire (nom, type, entreprise liée, *Ma position*, *Supprimer*) ; clic droit sur la carte = nouveau point à cet endroit.
Les mêmes droits que les commandes sont revérifiés côté serveur ; les commandes `/carte point ...` restent disponibles.

**Filtres** : bouton *Filtres* sur la grande carte (par type, « seulement ouvertes », Tout / Aucun) ; choix mémorisé par joueur.

Points stockés dans `world/data/minenorth_map_waypoints.dat`, synchronisés en direct à tous les joueurs.

## Config client
`config/minenorth_map-client.toml` : taille/position/zoom de la minicarte, coordonnées, guidage.

## API pour les autres mods (repères poussés par le serveur)
`fr.minenorth.map.api.MapApi` (appel par réflexion) : `setMarker(joueur, nom, dimension, x, y, z, couleur, guidage)`, `removeMarker`, `clearMarkers(préfixe)`.
Les repères n'existent que chez le joueur visé, ne sont pas sauvegardés et s'effacent à l'arrivée si le guidage est actif. Utilisé par le mod Secours (incendies, blessés).

## Licence

**Tous droits réservés - MineNorthRP.** Réutilisation, copie, modification, décompilation / ingénierie
inverse (y compris par outils d'intelligence artificielle) et utilisation pour entraîner une IA sont
**interdites** sans autorisation écrite. Voir [LICENSE](LICENSE).

## Configuration serveur et types de points
- La config serveur est dans `config/minenorth_map-server.toml` (type COMMON). Elle était auparavant dans `<monde>/serverconfig/` (type SERVER), qui revenait à zéro à chaque redémarrage sur certains serveurs : copier l'ancien fichier une fois.
- Types de base ajoutés automatiquement avant « autre » s'ils ne sont pas dans `types` : `recolte`, `traitement`, `garage`.
- `/carte point typegroupe <préfixe> <type>` change le type de tous les points dont le nom commence par le préfixe (ex. `Garage_`).
