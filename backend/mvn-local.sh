#!/usr/bin/env bash
# Maven avec le JDK et Maven installés hors du dépôt (et hors OneDrive).
# Dossier des outils : $CRIMPING_TOOLS, par défaut ~/dev-tools/crimping-pro
# Exemple : ./mvn-local.sh test
set -euo pipefail
TOOLS="${CRIMPING_TOOLS:-$HOME/dev-tools/crimping-pro}"
export JAVA_HOME="$TOOLS/jdk-21"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(dirname "$0")"
exec "$TOOLS/maven-3.9.9/bin/mvn" -B -ntp "$@"
