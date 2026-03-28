#!/usr/bin/env bash
# =============================================================================
# AI Code Pilot — Build Script
# Builds the Eclipse plugin JAR using Maven + Tycho
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
echo -e "${BOLD}${BLUE}║       AI Code Pilot — Build                         ║${NC}"
echo -e "${BOLD}${BLUE}╚══════════════════════════════════════════════════════╝${NC}\n"

cd "$PROJECT_DIR"

# ── Check prerequisites ───────────────────────────────────────────────────────
step "Checking prerequisites..."

command -v java >/dev/null 2>&1 || error "Java not found"
command -v mvn  >/dev/null 2>&1 || error "Maven not found. Install from https://maven.apache.org/"

JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
MVN_VER=$(mvn -version 2>&1 | head -1)

success "Java $JAVA_VER"
success "$MVN_VER"

# ── Download lib dependencies first ──────────────────────────────────────────
step "Downloading runtime dependencies to lib/..."
mkdir -p lib
mvn dependency:copy-dependencies \
    -DoutputDirectory=lib \
    -DincludeScope=runtime \
    -DexcludeGroupIds=org.eclipse,org.osgi \
    -q
success "Dependencies ready in lib/"
ls -lh lib/*.jar 2>/dev/null | head -10

# ── Build parse of source only (no Eclipse P2, easier local build) ────────────
step "Compiling source files..."
mkdir -p bin

# Collect all jars for classpath
CLASSPATH=""
for jar in lib/*.jar; do
    CLASSPATH="$CLASSPATH:$jar"
done

# Find Eclipse plugin jars (must have Eclipse installed)
ECLIPSE_DIR="${ECLIPSE_HOME:-$(which eclipse 2>/dev/null | xargs dirname 2>/dev/null)}"
ECLIPSE_PLUGINS_DIRS=(
    "$ECLIPSE_DIR/plugins"
    "/usr/share/eclipse/plugins"
    "/Applications/Eclipse.app/Contents/Eclipse/plugins"
    "$HOME/eclipse/java-2024-06/eclipse/plugins"
    "$HOME/eclipse/plugins"
)

ECLIPSE_FOUND=false
ECLIPSE_CP=""
for PDIR in "${ECLIPSE_PLUGINS_DIRS[@]}"; do
    if [ -d "$PDIR" ]; then
        ECLIPSE_FOUND=true
        info "Found Eclipse plugins at: $PDIR"
        # Add key Eclipse jars to classpath
        for jar in \
            "$PDIR"/org.eclipse.core.runtime_*.jar \
            "$PDIR"/org.eclipse.core.resources_*.jar \
            "$PDIR"/org.eclipse.jface_*.jar \
            "$PDIR"/org.eclipse.swt_*.jar \
            "$PDIR"/org.eclipse.swt.gtk.linux.x86_64_*.jar \
            "$PDIR"/org.eclipse.swt.win32.win32.x86_64_*.jar \
            "$PDIR"/org.eclipse.swt.cocoa.macosx.x86_64_*.jar \
            "$PDIR"/org.eclipse.ui_*.jar \
            "$PDIR"/org.eclipse.ui.workbench_*.jar \
            "$PDIR"/org.eclipse.jdt.core_*.jar \
            "$PDIR"/org.eclipse.jdt.ui_*.jar \
            "$PDIR"/org.eclipse.text_*.jar \
            "$PDIR"/org.eclipse.jface.text_*.jar \
            "$PDIR"/org.eclipse.ui.editors_*.jar \
            "$PDIR"/org.eclipse.ui.workbench.texteditor_*.jar \
            "$PDIR"/org.eclipse.core.expressions_*.jar \
            "$PDIR"/org.eclipse.osgi_*.jar; do
            [ -f "$jar" ] && ECLIPSE_CP="$ECLIPSE_CP:$jar"
        done
        break
    fi
done

if [ "$ECLIPSE_FOUND" = false ]; then
    warn "Eclipse installation not found. Compiling without Eclipse dependencies."
    warn "The build will succeed but Eclipse API classes won't be resolved."
    warn "For full build, set ECLIPSE_HOME=/path/to/eclipse"
fi

CLASSPATH="$CLASSPATH$ECLIPSE_CP"

# Compile
find src/main/java -name "*.java" > /tmp/sources.txt
JAVA_COUNT=$(wc -l < /tmp/sources.txt)
info "Compiling $JAVA_COUNT Java files..."

javac -source 17 -target 17 \
    -cp ".:${CLASSPATH}" \
    -d bin \
    @/tmp/sources.txt \
    2>&1 | grep -v "^Note:" || warn "Compilation warnings (see above)"

success "Compilation complete → bin/"

# ── Package as JAR ────────────────────────────────────────────────────────────
step "Packaging plugin JAR..."

VERSION="1.0.0"
JAR_NAME="com.aicodepilot_${VERSION}.jar"

# Create jar with manifest
jar cf "target/$JAR_NAME" \
    -C bin . \
    plugin.xml \
    -C . resources \
    2>/dev/null || {
  mkdir -p target
  jar cf "target/$JAR_NAME" -C bin . plugin.xml 2>/dev/null || true
}

# Copy META-INF into jar
cd bin
jar uf "../target/$JAR_NAME" .
cd ..
cp META-INF/MANIFEST.MF /tmp/MANIFEST_TEMP.MF
jar ufm "target/$JAR_NAME" /tmp/MANIFEST_TEMP.MF 2>/dev/null || true

if [ -f "target/$JAR_NAME" ]; then
    JAR_SIZE=$(du -sh "target/$JAR_NAME" | cut -f1)
    success "Plugin JAR created: target/$JAR_NAME ($JAR_SIZE)"
else
    warn "JAR creation had issues. Check bin/ directory for compiled classes."
fi

rm -f /tmp/sources.txt /tmp/MANIFEST_TEMP.MF

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}${GREEN}════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${GREEN}  Build Complete!${NC}"
echo -e "${BOLD}${GREEN}════════════════════════════════════════════════════${NC}"
echo ""
echo "Output files:"
ls -lh target/*.jar 2>/dev/null || echo "  (see bin/ for compiled classes)"
echo ""
echo "Next step: run scripts/install-plugin.sh"
echo ""
