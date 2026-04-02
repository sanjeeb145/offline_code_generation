# 🧠 AI Code Pilot — Eclipse Plugin

> **Offline AI-powered code assistance for Java developers.**  
> Zero cloud. Zero telemetry. Runs entirely on your machine.

---

## Quick Start (3 Steps)

```bash
# Step 1 – Download model + llama.cpp
bash scripts/download-model.sh

# Step 2 – Build plugin
bash scripts/build.sh

# Step 3 – Install to Eclipse
bash scripts/install-plugin.sh
```

**Windows:** Run `scripts\setup-windows.bat`  
**One command:** `bash scripts/setup-all.sh`

---

## What It Does

| Feature | Description |
|---|---|
| 🧠 **Code Analyzer** | Reads Java class → infers purpose → suggests improvements |
| ⚡ **Code Generator** | Generates Controller, Service, Entity, DTO, Repository, Tests |
| 🐛 **Debug Assistant** | Detects NPEs, SQL injection, thread-safety bugs, bad patterns |
| 🔄 **Refactoring Assistant** | Suggests design patterns, SOLID review, Java modernization |
| 🐳 **DevOps Assistant** | Generates Dockerfile, Kubernetes YAML, Kafka config, Compose |
| 💡 **Code Explainer** | Plain-English explanation of selected code |

---

## Project Structure

```
ai-code-pilot/
├── META-INF/
│   └── MANIFEST.MF              ← OSGi bundle manifest
├── plugin.xml                   ← Eclipse extension points
├── pom.xml                      ← Maven + Tycho build
├── build.properties             ← Eclipse PDE build config
├── .project                     ← Eclipse project file
├── .classpath                   ← Eclipse classpath
│
├── src/
│   ├── main/java/com/aicodepilot/
│   │   ├── Activator.java                    ← Plugin lifecycle (start/stop)
│   │   │
│   │   ├── model/
│   │   │   ├── AIRequest.java                ← Inference request (Builder pattern)
│   │   │   └── AIResponse.java               ← Inference response (immutable)
│   │   │
│   │   ├── engine/
│   │   │   ├── AIEngineManager.java          ← Engine selection + caching
│   │   │   ├── cache/
│   │   │   │   └── ResponseCache.java        ← LRU cache for responses
│   │   │   ├── llm/
│   │   │   │   ├── InferenceEngine.java      ← Interface for all engines
│   │   │   │   ├── LlamaInferenceEngine.java ← llama.cpp subprocess engine
│   │   │   │   ├── OnnxInferenceEngine.java  ← ONNX Runtime engine
│   │   │   │   ├── DJLInferenceEngine.java   ← Deep Java Library engine
│   │   │   │   ├── RuleBasedEngine.java      ← Fallback (no model needed)
│   │   │   │   └── CodeGenerationTranslator.java ← DJL tensor translator
│   │   │   └── embedding/
│   │   │       └── EmbeddingService.java     ← HNSW vector index for RAG
│   │   │
│   │   ├── analyzer/
│   │   │   ├── JavaCodeAnalyzer.java         ← AST + AI analysis
│   │   │   └── AnalysisResult.java           ← Analysis output value object
│   │   │
│   │   ├── generator/
│   │   │   └── CodeGenerator.java            ← Boilerplate + DevOps generation
│   │   │
│   │   ├── refactor/
│   │   │   └── RefactoringAssistant.java     ← Pattern + SOLID suggestions
│   │   │
│   │   ├── debug/
│   │   │   └── DebugAssistant.java           ← Bug + security detection
│   │   │
│   │   ├── devops/
│   │   │   └── DevOpsAssistant.java          ← Docker/K8s/Kafka generation
│   │   │
│   │   ├── ui/
│   │   │   ├── views/
│   │   │   │   ├── SuggestionsView.java      ← Main AI suggestions panel
│   │   │   │   ├── AnalyzerView.java         ← Code analysis view
│   │   │   │   └── DevOpsView.java           ← DevOps generation view
│   │   │   ├── handlers/
│   │   │   │   ├── AnalyzeCodeHandler.java   ← Ctrl+Shift+A handler
│   │   │   │   ├── GenerateCodeHandler.java  ← Ctrl+Shift+G handler
│   │   │   │   ├── DetectBugsHandler.java    ← Ctrl+Shift+B handler
│   │   │   │   ├── RefactorCodeHandler.java  ← Refactor menu handler
│   │   │   │   ├── DevOpsHandler.java        ← DevOps menu handler
│   │   │   │   └── ExplainCodeHandler.java   ← Ctrl+Shift+E handler
│   │   │   └── dialogs/
│   │   │       ├── GenerateCodeDialog.java   ← Code gen configuration dialog
│   │   │       ├── MainPreferencePage.java   ← Preferences page
│   │   │       └── ModelPreferencePage.java  ← Model settings sub-page
│   │   │
│   │   └── util/
│   │       ├── PluginLogger.java             ← SLF4J + Eclipse log bridge
│   │       ├── PromptTemplates.java          ← All AI system prompts
│   │       ├── EditorUtils.java              ← Editor text extraction helpers
│   │       └── ResultDialog.java             ← Scrollable AI output dialog
│   │
│   └── test/java/com/aicodepilot/
│       ├── analyzer/JavaCodeAnalyzerTest.java
│       ├── debug/DebugAssistantTest.java
│       ├── debug/DevOpsAssistantTest.java
│       ├── engine/AIEngineManagerTest.java
│       └── generator/CodeGeneratorTest.java
│
├── lib/                         ← Runtime JARs (populated by Maven)
├── resources/
│   ├── icons/                   ← Plugin toolbar/menu icons
│   ├── templates/               ← Code generation templates
│   └── native/                  ← llama.cpp binaries (platform-specific)
│
└── scripts/
    ├── setup-all.sh             ← One-command full setup
    ├── download-model.sh        ← Download GGUF model + llama.cpp
    ├── build.sh                 ← Compile and package plugin JAR
    ├── install-plugin.sh        ← Copy JAR to Eclipse dropins
    ├── run-dev.sh               ← Launch Eclipse in PDE dev mode
    ├── run-tests.sh             ← Run JUnit tests (no Eclipse needed)
    └── setup-windows.bat        ← Windows setup equivalent
```

---

## System Requirements

| Component | Minimum | Recommended |
|---|---|---|
| Java | JDK 17 | JDK 21 |
| RAM | 8 GB | 16 GB |
| Disk | 5 GB | 10 GB |
| CPU | 4 cores | 8+ cores |
| Eclipse | 2023-09 | 2024-06+ |

---

## Engine Selection (Auto-detected)

The plugin tries engines in this order, picking the first that loads:

```
1. llama.cpp  → if a .gguf file is found   (fastest CPU performance)
2. ONNX       → if a .onnx file is found   (good cross-platform)
3. DJL        → if a .pt file is found     (pure Java)
4. Rule-based → always available           (no model, pattern matching only)
```

### Recommended Models

| Model | File | RAM | Best For |
|---|---|---|---|
| DeepSeek-Coder-1.3B-Q4 | `deepseek-coder-1.3b-instruct.Q4_K_M.gguf` | 2 GB | 8 GB systems |
| Phi-2-Q4 | `phi-2.Q4_K_M.gguf` | 3 GB | General purpose |
| CodeLlama-7B-Q4 | `codellama-7b-instruct.Q4_K_M.gguf` | 6 GB | Best quality |

Download from: https://huggingface.co/TheBloke

---

## Running Locally — Step by Step

### Linux / macOS

```bash
# 1. Clone or unzip the project
cd ai-code-pilot/

# 2. Make scripts executable
chmod +x scripts/*.sh

# 3. Download model (~800 MB for 1.3B model)
bash scripts/download-model.sh

# 4. Build the plugin
bash scripts/build.sh

# 5. Install to Eclipse
bash scripts/install-plugin.sh

# 6. Restart Eclipse
eclipse -clean
```

### Windows

```batch
REM 1. Open Command Prompt in the project folder
REM 2. Run the Windows setup script
scripts\setup-windows.bat

REM 3. Manually download the .gguf model from HuggingFace
REM    Save to: %USERPROFILE%\.aicodepilot\models\

REM 4. Restart Eclipse
eclipse.exe -clean
```

### Eclipse PDE (Development Mode — for contributing)

```
1. Open Eclipse with PDE installed
   (Help → Eclipse Marketplace → search "Eclipse PDE")

2. File → Import → Existing Projects into Workspace
   → Browse to: ai-code-pilot/
   → Finish

3. Right-click plugin.xml
   → Run As → Eclipse Application

This launches a child Eclipse instance with your plugin
loaded live — no need to build a JAR. Changes take effect
after restarting the child Eclipse.
```

---

## Using the Plugin

### Keyboard Shortcuts

| Action | Shortcut (Win/Linux) | Shortcut (Mac) |
|---|---|---|
| Analyze Code | `Ctrl+Shift+A` | `Cmd+Shift+A` |
| Generate Code | `Ctrl+Shift+G` | `Cmd+Shift+G` |
| Detect Bugs | `Ctrl+Shift+B` | `Cmd+Shift+B` |
| Explain Code | `Ctrl+Shift+E` | `Cmd+Shift+E` |

### Views

```
Window → Show View → AI Code Pilot →
  ├── AI Code Suggestions   (main panel — all features)
  ├── AI Code Analyzer      (deep structural analysis)
  └── AI DevOps Assistant   (Docker/K8s/Kafka generation)
```

### Context Menu

Right-click any Java file or selected code → **AI Code Pilot →**

- Analyze Selection / Analyze File
- Explain Code
- Detect Bugs
- Suggest Refactoring
- Generate Code Here
- Generate DevOps Artifacts

---

## Configuration

`Window → Preferences → AI Code Pilot`

| Setting | Default | Description |
|---|---|---|
| Model File | (empty) | Path to your `.gguf` model file |
| llama.cpp Binary | (empty) | Path to `llama-cli` or `llama-cli.exe` |
| CPU Threads | (auto) | Threads for inference (default: cores/2) |
| Max New Tokens | 512 | Max tokens to generate |
| Context Size | 2048 | Context window in tokens |
| Temperature | 0.2 | Creativity (0=deterministic, 1=creative) |
| Enable RAG | true | Use project code as context |

---

## Performance Tips

**For 8 GB RAM:**
- Use 1.3B model (Q4_K_M quantization)
- Set Max Tokens = 256, Context = 1024, Threads = 4

**For 16 GB RAM:**
- Use 6.7B model for much better suggestions
- Set Max Tokens = 512, Context = 2048, Threads = 6

**Response times (DeepSeek-Coder 1.3B):**
- 4-core laptop: 2–5 seconds
- 8-core desktop: 1–2 seconds
- First response is slower (model loads into RAM)

---

## Security

- ✅ **100% offline** — no API calls, no internet required
- ✅ **No telemetry** — zero data collection
- ✅ **No API keys** — nothing to configure or store
- ✅ **Local files only** — model runs as a subprocess with no network access
- ✅ **Your code stays yours** — never leaves your machine

---

## Troubleshooting

**"AI engine not loaded"**
→ Check that a `.gguf` file exists in your model directory  
→ Check `Window → Error Log` for details

**Slow responses (>15s)**
→ Switch to a smaller model (1.3B instead of 7B)  
→ Reduce CPU threads to `nproc/2`  
→ Lower Max Tokens to 256

**Plugin doesn't appear after install**
→ Run Eclipse with `-clean` flag: `eclipse -clean`

**Build fails (Eclipse APIs not found)**
→ Set `ECLIPSE_HOME` to your Eclipse installation directory  
→ Or use Eclipse PDE (import project → Run As Eclipse Application)
