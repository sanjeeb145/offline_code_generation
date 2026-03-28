#!/usr/bin/env bash
# =============================================================================
# AI Code Pilot — Run in Eclipse PDE Development Mode
# Launches a second Eclipse instance with the plugin loaded (hot-reload dev)
# =============================================================================
set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; NC='\033[0m'; BOLD='\033[1m'

info()    { echo -e "${CYAN}[INFO]${NC}  $1"; }
success() { echo -e "${GREEN}[OK]${NC}    $1"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $1"; }
error()   { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }
step()    { echo -e "\n${BOLD}${YELLOW}▶ $1${NC}"; }

echo -e "\n${BOLD}${BLUE}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${BLUE}║     AI Code Pilot — PDE Dev Mode Launch             ║${NC}"
echo -e "${BOLD}${BLUE}╚══════════════════════════════════════════════════════╝${NC}\n"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# ── Find Eclipse ──────────────────────────────────────────────────────────────
ECLIPSE_BIN="${ECLIPSE_HOME}/eclipse"
[ -f "$ECLIPSE_BIN" ] || ECLIPSE_BIN=$(which eclipse 2>/dev/null || echo "")

if [ -z "$ECLIPSE_BIN" ] || [ ! -f "$ECLIPSE_BIN" ]; then
    read -rp "Enter full path to eclipse binary: " ECLIPSE_BIN
    [ -f "$ECLIPSE_BIN" ] || error "Eclipse binary not found: $ECLIPSE_BIN"
fi

success "Eclipse binary: $ECLIPSE_BIN"

# ── Dev workspace ─────────────────────────────────────────────────────────────
DEV_WORKSPACE="$HOME/.aicodepilot-dev-workspace"
mkdir -p "$DEV_WORKSPACE"
info "Dev workspace: $DEV_WORKSPACE"

# ── Launch instructions ───────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}For PDE Development (recommended for active development):${NC}"
echo ""
echo "  Option A — Import project in Eclipse (PDE):"
echo "  ─────────────────────────────────────────────"
echo "  1. Open Eclipse with PDE installed"
echo "  2. File → Import → Existing Projects into Workspace"
echo "  3. Browse to: $PROJECT_DIR"
echo "  4. Right-click plugin.xml → Run As → Eclipse Application"
echo "     (this launches a child Eclipse with your plugin loaded)"
echo ""
echo "  Option B — Launch directly (already imported):"
echo "  ─────────────────────────────────────────────"

# Create a minimal launch config
LAUNCH_CONFIG="$PROJECT_DIR/.launch/AICodePilot.launch"
mkdir -p "$(dirname "$LAUNCH_CONFIG")"

cat > "$LAUNCH_CONFIG" << 'EOF'
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<launchConfiguration type="org.eclipse.pde.ui.RuntimeWorkbench">
  <booleanAttribute key="automaticAdd" value="true"/>
  <booleanAttribute key="automaticValidate" value="false"/>
  <stringAttribute key="bootstrap" value=""/>
  <stringAttribute key="configLocation" value="${workspace_loc}/../.aicodepilot-dev-runtime"/>
  <booleanAttribute key="default" value="true"/>
  <booleanAttribute key="includeOptional" value="true"/>
  <stringAttribute key="location" value="${workspace_loc}/../.aicodepilot-dev-runtime"/>
  <booleanAttribute key="org.eclipse.jdt.launching.ATTR_USE_START_ON_FIRST_THREAD" value="true"/>
  <stringAttribute key="pde.version" value="3.3"/>
  <booleanAttribute key="show_selected_only" value="false"/>
  <stringAttribute key="templateConfig" value="${target_home}/configuration/config.ini"/>
  <booleanAttribute key="tracing" value="false"/>
  <booleanAttribute key="useCustomFeatures" value="false"/>
  <booleanAttribute key="useDefaultConfigArea" value="false"/>
  <booleanAttribute key="useDefaultDataLocation" value="false"/>
  <booleanAttribute key="useProduct" value="false"/>
  <listAttribute key="selectedPlugins"/>
</launchConfiguration>
EOF

info "Launch config created: $LAUNCH_CONFIG"

echo "  In Eclipse:"
echo "  Run → Run Configurations → Eclipse Application → AICodePilot"
echo ""
echo -e "${BOLD}For quick test (install mode):${NC}"
echo "  bash scripts/build.sh && bash scripts/install-plugin.sh"
echo ""

# Launch with -clean flag for fresh start
step "Launching Eclipse in clean mode (with plugin from dropins)..."
info "If the plugin isn't installed, run scripts/install-plugin.sh first."
echo ""

DROPINS_JAR=$(ls "$HOME/eclipse"/*/eclipse/dropins/com.aicodepilot_*.jar 2>/dev/null | head -1 || echo "")

if [ -z "$DROPINS_JAR" ]; then
    warn "Plugin not found in dropins. Running install first..."
    bash "$SCRIPT_DIR/install-plugin.sh"
fi

echo -e "${YELLOW}Launching Eclipse... close this terminal after Eclipse opens.${NC}"
"$ECLIPSE_BIN" -clean -data "$DEV_WORKSPACE" &

disown
success "Eclipse launched in background (PID: $!)"
