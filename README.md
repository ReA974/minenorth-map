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
/carte point ajouter "<nom>" [couleur]          à ta position
/carte point placer "<nom>" <x> <y> <z> [couleur]
/carte point deplacer "<nom>"                    déplace à ta position
/carte point supprimer "<nom>"
/carte point renommer "<ancien>" "<nouveau>"
/carte point couleur "<nom>" <couleur>
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

Couleurs : rouge, orange, jaune, vert, cyan, bleu, violet, rose, blanc, gris, noir, marron, ou hex `FF8800`.

Points stockés dans `world/data/minenorth_map_waypoints.dat`, synchronisés en direct à tous les joueurs.

## Config client
`config/minenorth_map-client.toml` : taille/position/zoom de la minicarte, coordonnées, guidage.

## API pour les autres mods (repères poussés par le serveur)
`fr.minenorth.map.api.MapApi` (appel par réflexion) : `setMarker(joueur, nom, dimension, x, y, z, couleur, guidage)`, `removeMarker`, `clearMarkers(préfixe)`.
Les repères n'existent que chez le joueur visé, ne sont pas sauvegardés et s'effacent à l'arrivée si le guidage est actif. Utilisé par le mod Secours (incendies, blessés).
