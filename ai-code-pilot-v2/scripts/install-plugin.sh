#!/usr/bin/env bash
# =============================================================================
# AI Code Pilot — Install Plugin to Eclipse
# =============================================================================
set -e
CYAN='\033[0;36m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
RED='\033[0;31m'; NC='\033[0m'; BOLD='\033[1m'
info()    { echo -e "${CYAN}[INFO]${NC}  $1"; }
success() { echo -e "${GREEN}[OK]${NC}    $1"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $1"; }
error()   { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

JAR="target/com.aicodepilot_1.0.0.jar"

# Build first if JAR missing
if [ ! -f "$JAR" ]; then
    warn "JAR not found — building first..."
    bash scripts/build.sh
fi

[ -f "$JAR" ] || error "JAR still missing after build: $JAR"

# Find Eclipse
ECLIPSE_CANDIDATES=(
    "$ECLIPSE_HOME"
    "$HOME/eclipse/java-2024-06/eclipse"
    "$HOME/eclipse/java-2023-09/eclipse"
    "$HOME/eclipse/jee-2024-06/eclipse"
    "$HOME/eclipse"
    "/opt/eclipse"
    "/usr/share/eclipse"
    "/Applications/Eclipse.app/Contents/Eclipse"
    "/Applications/Eclipse JEE.app/Contents/Eclipse"
)

ECLIPSE_DIR=""
for C in "${ECLIPSE_CANDIDATES[@]}"; do
    if [ -n "$C" ] && [ -d "$C/plugins" ]; then
        ECLIPSE_DIR="$C"
        break
    fi
done

if [ -z "$ECLIPSE_DIR" ]; then
    echo ""
    warn "Eclipse not auto-detected."
    read -rp "Enter Eclipse installation path: " ECLIPSE_DIR
    [ -d "$ECLIPSE_DIR" ] || error "Not found: $ECLIPSE_DIR"
fi

success "Eclipse: $ECLIPSE_DIR"
DROPINS="$ECLIPSE_DIR/dropins"
mkdir -p "$DROPINS"

# Remove old version
rm -f "$DROPINS"/com.aicodepilot_*.jar
info "Old version removed"

# Install new version
cp "$JAR" "$DROPINS/"
success "Installed: $(basename $JAR) → $DROPINS/"

echo ""
echo -e "${BOLD}${GREEN}Plugin installed!${NC}"
echo ""
echo "1. Restart Eclipse:  eclipse -clean"
echo "2. Open view:        Window → Show View → AI Code Pilot"
echo "3. Set model path:   Window → Preferences → AI Code Pilot → Model Settings"
echo ""
