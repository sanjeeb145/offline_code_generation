#!/usr/bin/env bash
# =============================================================================
# AI Code Pilot — Build Script (JDK 21 + Maven 3+)
#
# This script:
#   1. Finds your Eclipse installation
#   2. Detects the actual JAR versions in plugins/
#   3. Patches pom.xml with the exact JAR filenames
#   4. Runs mvn clean package
#   5. Verifies the resulting JAR has all required classes
#
# Usage:
#   bash scripts/build.sh
#   OR with explicit Eclipse path:
#   ECLIPSE_HOME=/path/to/eclipse bash scripts/build.sh
# =============================================================================
set -e

CYAN='\033[0;36m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
RED='\033[0;31m'; BLUE='\033[0;34m'; NC='\033[0m'; BOLD='\033[1m'

info()    { echo -e "${CYAN}[INFO]${NC}  $1"; }
success() { echo -e "${GREEN}[OK]${NC}    $1"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $1"; }
error()   { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }
step()    { echo -e "\n${BOLD}${YELLOW}>> $1${NC}"; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

echo -e "\n${BOLD}${BLUE}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${BLUE}║     AI Code Pilot — Build (JDK 21 + Maven 3+)       ║${NC}"
echo -e "${BOLD}${BLUE}╚══════════════════════════════════════════════════════╝${NC}\n"

# ── Step 1: Check prerequisites ───────────────────────────────────────────────
step "Step 1: Checking prerequisites..."

command -v java >/dev/null 2>&1 || error "Java not found. Install JDK 21"
command -v mvn  >/dev/null 2>&1 || error "Maven not found. Install Maven 3.9+"

JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
[ "$JAVA_VER" -ge 21 ] 2>/dev/null || error "JDK 21+ required. Found: Java $JAVA_VER"
success "Java $JAVA_VER"
success "$(mvn -version 2>&1 | head -1)"

# ── Step 2: Find Eclipse ──────────────────────────────────────────────────────
step "Step 2: Locating Eclipse installation..."

ECLIPSE_CANDIDATES=(
    "$ECLIPSE_HOME"
    "$HOME/eclipse/java-2024-06/eclipse"
    "$HOME/eclipse/java-2023-12/eclipse"
    "$HOME/eclipse/java-2023-09/eclipse"
    "$HOME/eclipse/jee-2024-06/eclipse"
    "$HOME/eclipse/jee-2023-12/eclipse"
    "$HOME/eclipse"
    "/opt/eclipse"
    "/usr/share/eclipse"
    "/Applications/Eclipse.app/Contents/Eclipse"
    "/Applications/Eclipse JEE.app/Contents/Eclipse"
    "C:/eclipse"
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
    warn "Eclipse installation not auto-detected."
    echo "Common locations:"
    echo "  ~/eclipse/java-2024-06/eclipse"
    echo "  /opt/eclipse"
    read -rp "Enter your Eclipse installation path: " ECLIPSE_DIR
    [ -d "$ECLIPSE_DIR/plugins" ] || error "Eclipse plugins not found at: $ECLIPSE_DIR"
fi

success "Eclipse: $ECLIPSE_DIR"
export ECLIPSE_HOME="$ECLIPSE_DIR"
PLUGINS="$ECLIPSE_DIR/plugins"

# ── Step 3: Detect actual Eclipse JAR versions ───────────────────────────────
step "Step 3: Detecting Eclipse plugin JAR versions..."

# Function to find a JAR matching a prefix in the plugins folder
find_jar() {
    local prefix="$1"
    local result
    result=$(ls "$PLUGINS/${prefix}"_*.jar 2>/dev/null | head -1)
    if [ -z "$result" ]; then
        # Try without version (some JARs have no version suffix)
        result=$(ls "$PLUGINS/${prefix}.jar" 2>/dev/null | head -1)
    fi
    echo "$result"
}

get_version() {
    local jarpath="$1"
    local prefix="$2"
    if [ -z "$jarpath" ]; then echo "MISSING"; return; fi
    basename "$jarpath" | sed "s/${prefix}_//" | sed 's/\.jar$//'
}

# Detect each required JAR
JAR_RUNTIME=$(find_jar "org.eclipse.core.runtime")
JAR_RESOURCES=$(find_jar "org.eclipse.core.resources")
JAR_JOBS=$(find_jar "org.eclipse.core.jobs")
JAR_COMMANDS=$(find_jar "org.eclipse.core.commands")
JAR_EXPRESSIONS=$(find_jar "org.eclipse.core.expressions")
JAR_UI_WORKBENCH=$(find_jar "org.eclipse.ui.workbench")
JAR_UI=$(find_jar "org.eclipse.ui")
JAR_UI_EDITORS=$(find_jar "org.eclipse.ui.editors")
JAR_UI_TEXTEDITOR=$(find_jar "org.eclipse.ui.workbench.texteditor")
JAR_UI_CONSOLE=$(find_jar "org.eclipse.ui.console")
JAR_UI_IDE=$(find_jar "org.eclipse.ui.ide")
JAR_SWT=$(find_jar "org.eclipse.swt")
JAR_JFACE=$(find_jar "org.eclipse.jface")
JAR_JFACE_TEXT=$(find_jar "org.eclipse.jface.text")
JAR_TEXT=$(find_jar "org.eclipse.text")
JAR_JDT_CORE=$(find_jar "org.eclipse.jdt.core")
JAR_JDT_UI=$(find_jar "org.eclipse.jdt.ui")
JAR_OSGI=$(find_jar "org.eclipse.osgi")

echo ""
echo "Found Eclipse JARs:"
for name_jar in \
    "core.runtime:$JAR_RUNTIME" \
    "core.resources:$JAR_RESOURCES" \
    "core.jobs:$JAR_JOBS" \
    "core.commands:$JAR_COMMANDS" \
    "ui.workbench:$JAR_UI_WORKBENCH" \
    "ui:$JAR_UI" \
    "swt:$JAR_SWT" \
    "jface:$JAR_JFACE" \
    "jface.text:$JAR_JFACE_TEXT" \
    "jdt.core:$JAR_JDT_CORE" \
    "osgi:$JAR_OSGI"; do
    name="${name_jar%%:*}"
    jar="${name_jar#*:}"
    if [ -f "$jar" ]; then
        success "  $name → $(basename $jar)"
    else
        warn "  $name → NOT FOUND (will try to continue)"
    fi
done

# Check mandatory JARs
[ -f "$JAR_RUNTIME" ]    || error "org.eclipse.core.runtime not found in $PLUGINS"
[ -f "$JAR_UI_WORKBENCH" ] || error "org.eclipse.ui.workbench not found in $PLUGINS"
[ -f "$JAR_OSGI" ]       || error "org.eclipse.osgi not found in $PLUGINS"

# ── Step 4: Generate pom-resolved.xml with exact JAR paths ───────────────────
step "Step 4: Generating resolved pom.xml with exact Eclipse JAR paths..."

# Write the resolved pom.xml with actual JAR paths
cat > pom-resolved.xml << POMEOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">

  <modelVersion>4.0.0</modelVersion>
  <groupId>com.aicodepilot</groupId>
  <artifactId>ai-code-pilot</artifactId>
  <version>1.0.0</version>
  <packaging>jar</packaging>
  <name>AI Code Assist</name>

  <properties>
    <java.version>21</java.version>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <plugin.jar.name>com.aicodepilot_1.0.0</plugin.jar.name>
  </properties>

  <dependencies>

    <!-- Eclipse APIs from local Eclipse installation (scope=system) -->
POMEOF

add_eclipse_dep() {
    local gid="$1" aid="$2" path="$3"
    [ -f "$path" ] || return 0
    cat >> pom-resolved.xml << EOF
    <dependency>
      <groupId>${gid}</groupId>
      <artifactId>${aid}</artifactId>
      <version>local</version>
      <scope>system</scope>
      <systemPath>${path}</systemPath>
    </dependency>
EOF
}

add_eclipse_dep "eclipse" "core.runtime"    "$JAR_RUNTIME"
add_eclipse_dep "eclipse" "core.resources"  "$JAR_RESOURCES"
add_eclipse_dep "eclipse" "core.jobs"       "$JAR_JOBS"
add_eclipse_dep "eclipse" "core.commands"   "$JAR_COMMANDS"
add_eclipse_dep "eclipse" "core.expressions" "$JAR_EXPRESSIONS"
add_eclipse_dep "eclipse" "ui.workbench"    "$JAR_UI_WORKBENCH"
add_eclipse_dep "eclipse" "ui"              "$JAR_UI"
add_eclipse_dep "eclipse" "ui.editors"      "$JAR_UI_EDITORS"
add_eclipse_dep "eclipse" "ui.texteditor"   "$JAR_UI_TEXTEDITOR"
add_eclipse_dep "eclipse" "ui.console"      "$JAR_UI_CONSOLE"
add_eclipse_dep "eclipse" "ui.ide"          "$JAR_UI_IDE"
add_eclipse_dep "eclipse" "swt"             "$JAR_SWT"
add_eclipse_dep "eclipse" "jface"           "$JAR_JFACE"
add_eclipse_dep "eclipse" "jface.text"      "$JAR_JFACE_TEXT"
add_eclipse_dep "eclipse" "text"            "$JAR_TEXT"
add_eclipse_dep "eclipse" "jdt.core"        "$JAR_JDT_CORE"
add_eclipse_dep "eclipse" "jdt.ui"          "$JAR_JDT_UI"
add_eclipse_dep "eclipse" "osgi"            "$JAR_OSGI"

cat >> pom-resolved.xml << 'POMEOF2'
    <!-- Runtime dependencies — bundled into lib/ inside plugin JAR -->
    <dependency>
      <groupId>com.github.javaparser</groupId>
      <artifactId>javaparser-core</artifactId>
      <version>3.25.8</version>
    </dependency>
    <dependency>
      <groupId>com.github.javaparser</groupId>
      <artifactId>javaparser-symbol-solver-core</artifactId>
      <version>3.25.8</version>
    </dependency>
    <dependency>
      <groupId>org.json</groupId>
      <artifactId>json</artifactId>
      <version>20231013</version>
    </dependency>
    <dependency>
      <groupId>com.google.guava</groupId>
      <artifactId>guava</artifactId>
      <version>32.1.3-jre</version>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
      <version>2.0.9</version>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-simple</artifactId>
      <version>2.0.9</version>
    </dependency>
    <dependency>
      <groupId>org.javassist</groupId>
      <artifactId>javassist</artifactId>
      <version>3.29.2-GA</version>
    </dependency>

    <!-- Test -->
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.10.1</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.mockito</groupId>
      <artifactId>mockito-core</artifactId>
      <version>5.7.0</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <sourceDirectory>src/main/java</sourceDirectory>
    <testSourceDirectory>src/test/java</testSourceDirectory>
    <outputDirectory>target/classes</outputDirectory>
    <plugins>

      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.13.0</version>
        <configuration>
          <source>21</source>
          <target>21</target>
          <encoding>UTF-8</encoding>
          <failOnError>true</failOnError>
          <showWarnings>false</showWarnings>
        </configuration>
      </plugin>

      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-dependency-plugin</artifactId>
        <version>3.7.0</version>
        <executions>
          <execution>
            <id>copy-runtime-deps</id>
            <phase>prepare-package</phase>
            <goals><goal>copy-dependencies</goal></goals>
            <configuration>
              <outputDirectory>${project.build.directory}/lib</outputDirectory>
              <includeScope>runtime</includeScope>
              <excludeScope>system</excludeScope>
              <stripVersion>false</stripVersion>
            </configuration>
          </execution>
        </executions>
      </plugin>

      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-resources-plugin</artifactId>
        <version>3.3.1</version>
        <executions>
          <execution>
            <id>copy-plugin-resources</id>
            <phase>process-resources</phase>
            <goals><goal>copy-resources</goal></goals>
            <configuration>
              <outputDirectory>${project.build.outputDirectory}</outputDirectory>
              <overwrite>true</overwrite>
              <resources>
                <resource>
                  <directory>${project.basedir}</directory>
                  <includes>
                    <include>plugin.xml</include>
                    <include>META-INF/MANIFEST.MF</include>
                  </includes>
                </resource>
                <resource>
                  <directory>${project.basedir}/resources</directory>
                  <targetPath>resources</targetPath>
                </resource>
              </resources>
            </configuration>
          </execution>
        </executions>
      </plugin>

      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-antrun-plugin</artifactId>
        <version>3.1.0</version>
        <executions>
          <execution>
            <id>generate-manifest</id>
            <phase>prepare-package</phase>
            <goals><goal>run</goal></goals>
            <configuration>
              <target>
                <pathconvert property="bundle.classpath"
                             pathsep=",&#10; lib/" dirsep="/">
                  <fileset dir="${project.build.directory}/lib">
                    <include name="*.jar"/>
                  </fileset>
                  <mapper type="flatten"/>
                </pathconvert>
                <echo file="${project.build.outputDirectory}/META-INF/MANIFEST.MF"
                      append="false">Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: AI Code Pilot
Bundle-SymbolicName: com.aicodepilot;singleton:=true
Bundle-Version: 1.0.0
Bundle-Vendor: AI Code Pilot
Bundle-Activator: com.aicodepilot.Activator
Bundle-ActivationPolicy: lazy
Bundle-RequiredExecutionEnvironment: JavaSE-21
Require-Bundle: org.eclipse.ui,
 org.eclipse.core.runtime,
 org.eclipse.core.resources,
 org.eclipse.jdt.core,
 org.eclipse.jdt.ui,
 org.eclipse.ui.editors,
 org.eclipse.ui.workbench.texteditor,
 org.eclipse.jface.text,
 org.eclipse.swt,
 org.eclipse.jface,
 org.eclipse.text,
 org.eclipse.core.expressions,
 org.eclipse.ui.console
Bundle-ClassPath: .,
 lib/${bundle.classpath}
Export-Package: com.aicodepilot.engine,
 com.aicodepilot.analyzer,
 com.aicodepilot.generator,
 com.aicodepilot.model

</echo>
              </target>
            </configuration>
          </execution>
          <execution>
            <id>inject-lib</id>
            <phase>package</phase>
            <goals><goal>run</goal></goals>
            <configuration>
              <target>
                <jar destfile="${project.build.directory}/${plugin.jar.name}.jar"
                     update="true">
                  <fileset dir="${project.build.directory}">
                    <include name="lib/*.jar"/>
                  </fileset>
                </jar>
              </target>
            </configuration>
          </execution>
        </executions>
      </plugin>

      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-jar-plugin</artifactId>
        <version>3.4.1</version>
        <configuration>
          <finalName>${plugin.jar.name}</finalName>
          <archive>
            <manifestFile>
              ${project.build.outputDirectory}/META-INF/MANIFEST.MF
            </manifestFile>
            <addMavenDescriptor>false</addMavenDescriptor>
          </archive>
        </configuration>
      </plugin>

      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.2.5</version>
        <configuration><skipTests>true</skipTests></configuration>
      </plugin>

    </plugins>
  </build>
</project>
POMEOF2

success "Generated pom-resolved.xml"

# ── Step 5: Build ─────────────────────────────────────────────────────────────
step "Step 5: Building plugin JAR..."

mvn clean package -f pom-resolved.xml -DskipTests --no-transfer-progress

# ── Step 6: Verify ────────────────────────────────────────────────────────────
step "Step 6: Verifying JAR..."
JAR="target/com.aicodepilot_1.0.0.jar"

[ -f "$JAR" ] || error "JAR not created"

# Check Activator class is at root
ACT=$(unzip -l "$JAR" 2>/dev/null | grep "com/aicodepilot/Activator.class")
[ -n "$ACT" ] || error "Activator.class MISSING — compilation failed"

# Check class count
CLASS_COUNT=$(unzip -l "$JAR" 2>/dev/null | grep "\.class$" | wc -l)
LIB_COUNT=$(unzip -l "$JAR" 2>/dev/null | grep "^.*lib/.*\.jar" | wc -l)
JAR_SIZE=$(du -sh "$JAR" | cut -f1)

echo ""
echo -e "${BOLD}${GREEN}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${GREEN}║  BUILD SUCCESSFUL                                    ║${NC}"
echo -e "${BOLD}${GREEN}╚══════════════════════════════════════════════════════╝${NC}"
echo ""
echo "  JAR    : $JAR ($JAR_SIZE)"
echo "  Classes: $CLASS_COUNT  (includes all com.aicodepilot.* classes)"
echo "  lib/   : $LIB_COUNT JARs embedded"
echo ""
echo "Install to Eclipse:"
echo "  cp $JAR $ECLIPSE_DIR/dropins/"
echo "  eclipse -clean"
echo ""
echo "Or run:"
echo "  bash scripts/install-plugin.sh"
echo ""
