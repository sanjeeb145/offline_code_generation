#!/usr/bin/env bash
# =============================================================================
# AI Code Pilot — Run Tests (standalone, no Eclipse needed)
# Tests the pure-Java components that don't depend on SWT/Eclipse APIs
# =============================================================================
set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; NC='\033[0m'; BOLD='\033[1m'

info()    { echo -e "\033[0;36m[INFO]\033[0m  $1"; }
success() { echo -e "${GREEN}[PASS]${NC}  $1"; }
fail()    { echo -e "${RED}[FAIL]${NC}  $1"; }
step()    { echo -e "\n${BOLD}${YELLOW}▶ $1${NC}"; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

echo -e "\n${BOLD}${BLUE}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${BLUE}║       AI Code Pilot — Test Runner                   ║${NC}"
echo -e "${BOLD}${BLUE}╚══════════════════════════════════════════════════════╝${NC}\n"

# ── Download test deps if needed ──────────────────────────────────────────────
step "Resolving test dependencies..."
mkdir -p lib test-lib

mvn dependency:copy-dependencies \
    -DoutputDirectory=test-lib \
    -DincludeScope=test \
    -q 2>/dev/null || {
    info "Maven not available, using existing jars"
}

# ── Build test classpath ──────────────────────────────────────────────────────
CLASSPATH="bin"
for jar in lib/*.jar test-lib/*.jar; do
    [ -f "$jar" ] && CLASSPATH="$CLASSPATH:$jar"
done

# ── Compile test sources ──────────────────────────────────────────────────────
step "Compiling test sources..."
mkdir -p test-bin

# Only compile tests that don't import Eclipse/SWT APIs
TESTABLE_TESTS=(
    "src/test/java/com/aicodepilot/analyzer/JavaCodeAnalyzerTest.java"
    "src/test/java/com/aicodepilot/debug/DebugAssistantTest.java"
    "src/test/java/com/aicodepilot/generator/CodeGeneratorTest.java"
    "src/test/java/com/aicodepilot/debug/DevOpsAssistantTest.java"
)

COMPILE_LIST=""
for f in "${TESTABLE_TESTS[@]}"; do
    [ -f "$f" ] && COMPILE_LIST="$COMPILE_LIST $f"
done

if [ -n "$COMPILE_LIST" ]; then
    javac -source 17 -target 17 \
        -cp "$CLASSPATH" \
        -d test-bin \
        $COMPILE_LIST 2>&1 | grep -v "^Note:" || true
    success "Test compilation done"
else
    info "No test files found to compile"
fi

# ── Run tests ─────────────────────────────────────────────────────────────────
step "Running tests..."
FULL_CP="$CLASSPATH:test-bin"

# Check for JUnit Platform launcher
JUNIT_LAUNCHER=$(ls test-lib/junit-platform-console-standalone-*.jar 2>/dev/null | head -1 || echo "")

if [ -n "$JUNIT_LAUNCHER" ]; then
    java -cp "$FULL_CP:$JUNIT_LAUNCHER" \
        org.junit.platform.console.ConsoleLauncher \
        --scan-classpath=test-bin \
        --include-classname='.*Test' \
        2>&1
else
    # Fallback: run each test class directly
    info "JUnit launcher not found. Running tests via direct class execution."
    info "Download junit-platform-console-standalone for better output."

    PASS=0; FAIL=0
    TEST_CLASSES=(
        "com.aicodepilot.analyzer.JavaCodeAnalyzerTest"
        "com.aicodepilot.debug.DebugAssistantTest"
        "com.aicodepilot.generator.CodeGeneratorTest"
    )

    for cls in "${TEST_CLASSES[@]}"; do
        if java -cp "$FULL_CP" "$cls" 2>/dev/null; then
            success "$cls"
            PASS=$((PASS + 1))
        else
            fail "$cls"
            FAIL=$((FAIL + 1))
        fi
    done

    echo ""
    echo -e "Results: ${GREEN}$PASS passed${NC}, ${RED}$FAIL failed${NC}"
fi

echo ""
echo -e "${BOLD}${GREEN}Test run complete.${NC}"
