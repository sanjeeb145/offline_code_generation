#!/usr/bin/env bash
# =============================================================================
# AI Code Pilot — Install Plugin into Eclipse
# Copies the built JAR to Eclipse dropins folder and restarts Eclipse
# =============================================================================
set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; NC='\033[0m'; BOLD='\033[1m'

info()    { echo -e "${CYAN}[INFO]${NC}  $1"; }
success() { echo -e "${GREEN}[OK]${NC}    $1"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $1"; }
error()   { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }
step()    { echo -e "\n${BOLD}${YELLOW}▶ $1${NC}"; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo -e "\n${BOLD}${BLUE}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${BLUE}║       AI Code Pilot — Install                       ║${NC}"
echo -e "${BOLD}${BLUE}╚══════════════════════════════════════════════════════╝${NC}\n"

cd "$PROJECT_DIR"

# ── Find plugin JAR ───────────────────────────────────────────────────────────
step "Locating plugin JAR..."
PLUGIN_JAR=$(ls target/com.aicodepilot_*.jar 2>/dev/null | head -1)
if [ -z "$PLUGIN_JAR" ]; then
    warn "Plugin JAR not found in target/. Running build first..."
    bash scripts/build.sh
    PLUGIN_JAR=$(ls target/com.aicodepilot_*.jar 2>/dev/null | head -1)
fi
[ -z "$PLUGIN_JAR" ] && error "Could not find or build plugin JAR"
success "Found JAR: $PLUGIN_JAR"

# ── Find Eclipse installation ─────────────────────────────────────────────────
step "Locating Eclipse installation..."

ECLIPSE_CANDIDATES=(
    "$ECLIPSE_HOME"
    "/opt/eclipse"
    "/usr/share/eclipse"
    "$HOME/eclipse/java-2024-06/eclipse"
    "$HOME/eclipse/java-2023-09/eclipse"
    "$HOME/eclipse"
    "/Applications/Eclipse.app/Contents/Eclipse"
    "/Applications/Eclipse JEE.app/Contents/Eclipse"
    "C:/eclipse"
    "C:/Program Files/Eclipse"
)

ECLIPSE_DIR=""
for CANDIDATE in "${ECLIPSE_CANDIDATES[@]}"; do
    if [ -n "$CANDIDATE" ] && [ -d "$CANDIDATE" ] && \
       ([ -f "$CANDIDATE/eclipse" ] || [ -f "$CANDIDATE/eclipse.exe" ] || \
        [ -d "$CANDIDATE/plugins" ]); then
        ECLIPSE_DIR="$CANDIDATE"
        break
    fi
done

if [ -z "$ECLIPSE_DIR" ]; then
    echo ""
    warn "Eclipse installation not auto-detected."
    read -rp "Enter your Eclipse installation path: " ECLIPSE_DIR
    [ -d "$ECLIPSE_DIR" ] || error "Directory not found: $ECLIPSE_DIR"
fi

success "Eclipse found at: $ECLIPSE_DIR"

# ── Create dropins directory and install ─────────────────────────────────────
DROPINS_DIR="$ECLIPSE_DIR/dropins"
step "Installing to: $DROPINS_DIR"

mkdir -p "$DROPINS_DIR"

# Remove old version first
rm -f "$DROPINS_DIR"/com.aicodepilot_*.jar
info "Old version removed"

cp "$PLUGIN_JAR" "$DROPINS_DIR/"
success "Plugin installed: $(basename "$PLUGIN_JAR")"

# Also copy dependency JARs that Eclipse doesn't provide
step "Installing dependencies to dropins..."
for jar in lib/*.jar; do
    [ -f "$jar" ] && cp "$jar" "$DROPINS_DIR/" && info "  → $(basename "$jar")"
done

# ── Verify install ────────────────────────────────────────────────────────────
step "Verifying installation..."
ls -lh "$DROPINS_DIR"/com.aicodepilot_*.jar

# ── Offer to clean Eclipse OSGi cache ────────────────────────────────────────
echo ""
echo -e "${YELLOW}TIP: If the plugin doesn't appear after restarting Eclipse,${NC}"
echo -e "${YELLOW}     clear the OSGi cache by running Eclipse with:${NC}"
echo -e "${YELLOW}     eclipse -clean${NC}"
echo ""

# ── Summary ───────────────────────────────────────────────────────────────────
echo -e "${BOLD}${GREEN}════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${GREEN}  Installation Complete!${NC}"
echo -e "${BOLD}${GREEN}════════════════════════════════════════════════════${NC}"
echo ""
echo "Steps to complete setup:"
echo ""
echo "  1. RESTART Eclipse"
echo "     (or run:  eclipse -clean  to also clear cache)"
echo ""
echo "  2. Open AI Suggestions view:"
echo "     Window → Show View → AI Code Pilot → AI Code Suggestions"
echo ""
echo "  3. Configure model path:"
echo "     Window → Preferences → AI Code Pilot → Model Settings"
echo "     Model file: $HOME/.aicodepilot/models/<model>.gguf"
echo ""
echo "  4. Right-click any Java file → AI Code Pilot → Analyze Code"
echo ""
