#!/usr/bin/env bash
# =============================================================================
# Complete JDK 21 Installation and Project Build Script
# =============================================================================

set -e

echo "=========================================="
echo "Installing OpenJDK 21 and setting up project"
echo "=========================================="

# Step 1: Update package list
echo "Step 1: Updating package list..."
sudo apt update

# Step 2: Install OpenJDK 21
echo "Step 2: Installing OpenJDK 21..."
sudo apt install -y openjdk-21-jdk

# Step 3: Set JAVA_HOME and PATH
echo "Step 3: Setting Java environment variables..."
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# Make it permanent
echo "export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64" >> ~/.bashrc
echo "export PATH=\$JAVA_HOME/bin:\$PATH" >> ~/.bashrc

# Step 4: Verify installation
echo "Step 4: Verifying Java installation..."
java -version
echo "Javac version:"
javac -version

# Step 5: Navigate to project directory and build
echo "Step 5: Building the project..."
cd /workspaces/offline_code_generation/ai-code-pilot

echo "Current directory: $(pwd)"
echo "Running Maven clean compile..."

# Run Maven build
mvn clean compile

echo "=========================================="
echo "Installation and build complete!"
echo "=========================================="
echo "JAVA_HOME: $JAVA_HOME"
echo "Java version: $(java -version 2>&1 | head -1)"
echo ""
echo "To apply environment changes in new terminals, run:"
echo "source ~/.bashrc"