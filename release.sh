#!/usr/bin/env bash
# Crée une release : demande la version, met à jour le pom, crée release-<version>, commit et push.
set -euo pipefail

cd "$(dirname "$0")"

# IntelliJ ne charge pas ~/.bashrc : on charge nvm si présent (pour npm).
export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
[ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"

POM="backend/pom.xml"
VERSION_REGEX='^([A-Za-z0-9]+(-[A-Za-z0-9]+)*-)?[0-9]+\.[0-9]+\.[0-9]+$'

# --- Maven : wrapper si disponible, sinon mvn ---
if [ -x backend/mvnw ]; then MVN="backend/mvnw"; else MVN="mvn"; fi
command -v "$MVN" >/dev/null 2>&1 || [ -x "$MVN" ] || { echo "[ERREUR] Maven introuvable."; exit 1; }

# --- Contrôles git ---
if [ -n "$(git status --porcelain)" ]; then
    echo "[ERREUR] Des modifications non commitées existent. Commitez ou stashez d'abord."
    git status --short
    exit 1
fi

BRANCHE_COURANTE="$(git rev-parse --abbrev-ref HEAD)"
echo "Branche de départ : $BRANCHE_COURANTE"
git fetch origin --prune

# --- Version actuelle ---
ACTUELLE="$($MVN -q -f "$POM" help:evaluate -Dexpression=project.version -DforceStdout 2>/dev/null | tail -n1)"
echo "Version actuelle du pom : $ACTUELLE"

# Proposition : +1 sur le dernier nombre (patch)
PREFIXE="${ACTUELLE%.*}"
PATCH="${ACTUELLE##*.}"
if [[ "$PATCH" =~ ^[0-9]+$ ]]; then PROPOSEE="$PREFIXE.$((PATCH + 1))"; else PROPOSEE=""; fi

read -r -p "Nouvelle version [${PROPOSEE}] : " NOUVELLE
NOUVELLE="${NOUVELLE:-$PROPOSEE}"
NOUVELLE="$(printf '%s' "$NOUVELLE" | tr -d ' ')"

if ! [[ "$NOUVELLE" =~ $VERSION_REGEX ]]; then
    echo "[ERREUR] Version invalide : '$NOUVELLE' (formats : 1.0.7 ou moussa-1.0.6)."
    exit 1
fi
if [ "$NOUVELLE" = "$ACTUELLE" ]; then
    echo "[ERREUR] La version est identique à l'actuelle."
    exit 1
fi

BRANCHE="release-$NOUVELLE"
if git show-ref --verify --quiet "refs/heads/$BRANCHE" \
   || git ls-remote --exit-code --heads origin "$BRANCHE" >/dev/null 2>&1; then
    echo "[ERREUR] La branche $BRANCHE existe déjà (locale ou distante)."
    exit 1
fi

read -r -p "Notes de version (facultatif) : " NOTES

echo
echo "Résumé :"
echo "  $ACTUELLE  ->  $NOUVELLE"
echo "  Branche    : $BRANCHE (depuis $BRANCHE_COURANTE)"
read -r -p "Créer la branche et modifier les fichiers ? [o/N] " OK
[[ "$OK" =~ ^[oOyY]$ ]] || { echo "Annulé."; exit 0; }

# --- Création de la branche et changement de version ---
git checkout -b "$BRANCHE"

"$MVN" -q -f "$POM" versions:set -DnewVersion="$NOUVELLE" -DgenerateBackupPoms=false

# Facultatif : aligner aussi la version de l'UI.
# (cd ui && npm version "${NOUVELLE##*-}" --no-git-tag-version --allow-same-version)

git add -A
git commit -m "Release $NOUVELLE${NOTES:+ : $NOTES}"

echo
read -r -p "Pousser $BRANCHE maintenant ? Cela LANCE le workflow GitHub. [o/N] " PUSH
if [[ "$PUSH" =~ ^[oOyY]$ ]]; then
    git push -u origin "$BRANCHE"
    echo "Branche poussée : le workflow Build Windows Installer démarre."
else
    echo "Non poussée. Pour lancer plus tard : git push -u origin $BRANCHE"
fi

# On revient sur la branche d'origine.
git checkout "$BRANCHE_COURANTE"