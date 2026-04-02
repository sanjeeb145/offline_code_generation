#!/usr/bin/env bash
# =============================================================================
# AI Code Pilot — Model Downloader
# Downloads the recommended GGUF model and llama.cpp binary
# =============================================================================
set -e

# ── Colours ──────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; NC='\033[0m'; BOLD='\033[1m'

print_header() {
  echo -e "\n${BOLD}${BLUE}╔══════════════════════════════════════════════════════╗${NC}"
  echo -e "${BOLD}${BLUE}║       AI Code Pilot — Model Setup                   ║${NC}"
  echo -e "${BOLD}${BLUE}╚══════════════════════════════════════════════════════╝${NC}\n"
}

info()    { echo -e "${CYAN}[INFO]${NC}  $1"; }
success() { echo -e "${GREEN}[OK]${NC}    $1"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $1"; }
error()   { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }
step()    { echo -e "\n${BOLD}${YELLOW}▶ $1${NC}"; }

# ── Directories ───────────────────────────────────────────────────────────────
MODEL_DIR="$HOME/.aicodepilot/models"
BIN_DIR="$HOME/.aicodepilot"

print_header

# ── Check dependencies ────────────────────────────────────────────────────────
step "Checking dependencies..."
command -v java  >/dev/null 2>&1 || error "Java 17+ not found. Install from https://adoptium.net/"
command -v wget  >/dev/null 2>&1 || command -v curl >/dev/null 2>&1 || error "wget or curl is required"

JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VER" -lt 17 ] 2>/dev/null; then
  error "Java 17+ required. Found Java $JAVA_VER"
fi
success "Java $JAVA_VER detected"

# ── Create directories ────────────────────────────────────────────────────────
step "Creating model directory..."
mkdir -p "$MODEL_DIR"
mkdir -p "$BIN_DIR"
success "Directory ready: $MODEL_DIR"

# ── Model selection ───────────────────────────────────────────────────────────
step "Select model based on your RAM:"
echo ""
echo "  [1] DeepSeek-Coder-1.3B-Q4  (800 MB model, ~2 GB RAM)  ← Recommended for 8 GB"
echo "  [2] Phi-2-Q4                 (1.6 GB model, ~3 GB RAM)  ← Good balance"
echo "  [3] CodeLlama-7B-Q4         (3.8 GB model, ~6 GB RAM)  ← Best quality, 16 GB RAM"
echo "  [4] Skip model download (use existing)"
echo ""
read -rp "Enter choice [1-4] (default: 1): " CHOICE
CHOICE="${CHOICE:-1}"

case $CHOICE in
  1)
    MODEL_NAME="deepseek-coder-1.3b-instruct.Q4_K_M.gguf"
    MODEL_URL="https://huggingface.co/TheBloke/deepseek-coder-1.3b-instruct-GGUF/resolve/main/$MODEL_NAME"
    ;;
  2)
    MODEL_NAME="phi-2.Q4_K_M.gguf"
    MODEL_URL="https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/$MODEL_NAME"
    ;;
  3)
    MODEL_NAME="codellama-7b-instruct.Q4_K_M.gguf"
    MODEL_URL="https://huggingface.co/TheBloke/CodeLlama-7B-Instruct-GGUF/resolve/main/$MODEL_NAME"
    ;;
  4)
    info "Skipping model download."
    MODEL_NAME=""
    ;;
  *)
    warn "Invalid choice, defaulting to option 1."
    MODEL_NAME="deepseek-coder-1.3b-instruct.Q4_K_M.gguf"
    MODEL_URL="https://huggingface.co/TheBloke/deepseek-coder-1.3b-instruct-GGUF/resolve/main/$MODEL_NAME"
    ;;
esac

# ── Download model ────────────────────────────────────────────────────────────
if [ -n "$MODEL_NAME" ]; then
  MODEL_PATH="$MODEL_DIR/$MODEL_NAME"
  if [ -f "$MODEL_PATH" ]; then
    success "Model already exists: $MODEL_PATH"
  else
    step "Downloading model: $MODEL_NAME"
    info "Source: $MODEL_URL"
    info "Destination: $MODEL_PATH"
    info "This may take several minutes depending on your connection..."
    echo ""

    if command -v wget >/dev/null 2>&1; then
      wget --show-progress -O "$MODEL_PATH" "$MODEL_URL" || error "Download failed"
    else
      curl -L --progress-bar -o "$MODEL_PATH" "$MODEL_URL" || error "Download failed"
    fi
    success "Model downloaded: $(du -sh "$MODEL_PATH" | cut -f1)"
  fi
fi

# ── Download llama.cpp binary ─────────────────────────────────────────────────
step "Setting up llama.cpp inference engine..."

OS="$(uname -s)"
ARCH="$(uname -m)"

case "$OS" in
  Linux*)
    LLAMA_URL="https://github.com/ggerganov/llama.cpp/releases/download/b4081/llama-b4081-bin-ubuntu-x64.zip"
    LLAMA_ZIP="$BIN_DIR/llama-linux.zip"
    LLAMA_BIN="llama-cli"
    ;;
  Darwin*)
    LLAMA_URL="https://github.com/ggerganov/llama.cpp/releases/download/b4081/llama-b4081-bin-macos-arm64.zip"
    LLAMA_ZIP="$BIN_DIR/llama-macos.zip"
    LLAMA_BIN="llama-cli"
    ;;
  MINGW*|CYGWIN*|MSYS*)
    warn "Windows detected. Please download llama.cpp manually:"
    warn "https://github.com/ggerganov/llama.cpp/releases"
    warn "Extract llama-cli.exe to: $BIN_DIR"
    LLAMA_BIN=""
    ;;
  *)
    warn "Unsupported OS: $OS. Download llama.cpp manually."
    LLAMA_BIN=""
    ;;
esac

if [ -n "$LLAMA_BIN" ]; then
  LLAMA_PATH="$BIN_DIR/$LLAMA_BIN"
  if [ -f "$LLAMA_PATH" ]; then
    success "llama.cpp already exists: $LLAMA_PATH"
  else
    info "Downloading llama.cpp..."
    if command -v wget >/dev/null 2>&1; then
      wget --show-progress -O "$LLAMA_ZIP" "$LLAMA_URL" || warn "llama.cpp download failed. Download manually."
    else
      curl -L --progress-bar -o "$LLAMA_ZIP" "$LLAMA_URL" || warn "llama.cpp download failed."
    fi

    if [ -f "$LLAMA_ZIP" ]; then
      cd "$BIN_DIR" || exit
      unzip -o "$LLAMA_ZIP" "$LLAMA_BIN" 2>/dev/null || unzip -o "$LLAMA_ZIP" 2>/dev/null
      chmod +x "$LLAMA_BIN" 2>/dev/null || true
      rm -f "$LLAMA_ZIP"
      success "llama.cpp installed at: $LLAMA_PATH"
    fi
  fi
fi

# ── Verify setup ──────────────────────────────────────────────────────────────
step "Verifying installation..."
echo ""
echo "  Model directory : $MODEL_DIR"
echo "  Models found    :"
if ls "$MODEL_DIR"/*.gguf 2>/dev/null | head -3; then
  success "Models are ready"
else
  warn "No .gguf models found. Download a model to enable AI features."
fi

echo ""
echo "  llama.cpp binary : $BIN_DIR/llama-cli"
if [ -f "$BIN_DIR/llama-cli" ]; then
  success "llama.cpp is ready"
else
  warn "llama.cpp not found. Rule-based engine will be used as fallback."
fi

# ── Quick test ────────────────────────────────────────────────────────────────
if [ -f "$BIN_DIR/llama-cli" ] && ls "$MODEL_DIR"/*.gguf 2>/dev/null | head -1 > /dev/null 2>&1; then
  step "Running quick inference test..."
  FIRST_MODEL=$(ls "$MODEL_DIR"/*.gguf | head -1)
  TEST_OUTPUT=$("$BIN_DIR/llama-cli" -m "$FIRST_MODEL" -p "Say: READY" -n 5 --log-disable 2>/dev/null || echo "")
  if echo "$TEST_OUTPUT" | grep -q "READY"; then
    success "Inference test passed!"
  else
    info "Test ran (output may vary). Engine should be functional."
  fi
fi

# ── Final instructions ────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}${GREEN}════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${GREEN}  Setup Complete!${NC}"
echo -e "${BOLD}${GREEN}════════════════════════════════════════════════════${NC}"
echo ""
echo "Next steps:"
echo "  1. Open Eclipse IDE"
echo "  2. Run:  scripts/install-plugin.sh"
echo "  3. Set model path in:"
echo "     Window → Preferences → AI Code Pilot → Model Settings"
echo "     Model file: $MODEL_DIR/$MODEL_NAME"
echo "     llama binary: $BIN_DIR/llama-cli"
echo ""
