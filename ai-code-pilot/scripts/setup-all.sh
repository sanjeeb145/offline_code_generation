#!/usr/bin/env bash
# =============================================================================
# AI Code Pilot — One-Command Full Setup
# Runs: download model → build plugin → install to Eclipse → done
# =============================================================================

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; NC='\033[0m'; BOLD='\033[1m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo -e "\n${BOLD}${BLUE}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${BLUE}║   AI Code Pilot — Full Setup (One Command)          ║${NC}"
echo -e "${BOLD}${BLUE}╚══════════════════════════════════════════════════════╝${NC}"
echo ""
echo "This will:"
echo "  1. Download a GGUF model (~800 MB)"
echo "  2. Download llama.cpp binary"
echo "  3. Build the Eclipse plugin"
echo "  4. Install the plugin to Eclipse"
echo ""
read -rp "Continue? [Y/n]: " CONFIRM
[[ "${CONFIRM:-Y}" =~ ^[Yy]$ ]] || exit 0

set -e

echo ""
echo -e "${BOLD}Step 1/3: Download AI model and llama.cpp${NC}"
bash "$SCRIPT_DIR/download-model.sh"

echo ""
echo -e "${BOLD}Step 2/3: Build plugin${NC}"
bash "$SCRIPT_DIR/build.sh"

echo ""
echo -e "${BOLD}Step 3/3: Install plugin to Eclipse${NC}"
bash "$SCRIPT_DIR/install-plugin.sh"

echo ""
echo -e "${BOLD}${GREEN}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${GREEN}║  ALL DONE! Restart Eclipse and open:                ║${NC}"
echo -e "${BOLD}${GREEN}║  Window → Show View → AI Code Pilot                 ║${NC}"
echo -e "${BOLD}${GREEN}╚══════════════════════════════════════════════════════╝${NC}"
echo ""
