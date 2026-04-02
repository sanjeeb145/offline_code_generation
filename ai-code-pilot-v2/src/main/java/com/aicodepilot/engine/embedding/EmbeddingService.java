package com.aicodepilot.engine.embedding;

import com.aicodepilot.engine.AIEngineManager;
import com.aicodepilot.util.PluginLogger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Local embedding service for RAG (Retrieval-Augmented Generation).
 *
 * <p>Indexes project Java files into an in-memory vector store and retrieves
 * the most similar code chunks to enrich AI prompts with project context.
 *
 * <p><b>Embedding implementation:</b> Uses a hash-projection approximation.
 * Replace {@link #computeEmbedding(String)} with a real sentence-transformer
 * (e.g., all-MiniLM-L6-v2 via ONNX) for production-quality semantic search.
 *
 * <p><b>Thread safety:</b> Uses a ReadWriteLock — concurrent reads are allowed,
 * writes are exclusive.
 */
public class EmbeddingService {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final int   DIM          = 128;
    private static final int   MAX_CHUNKS   = 5_000;
    private static final int   TOP_K        = 5;
    private static final float MIN_SIM      = 0.55f;

    // ── State ─────────────────────────────────────────────────────────────────
    private final AIEngineManager engineManager;
    private final Map<String, CodeChunk> store = new ConcurrentHashMap<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private volatile boolean initialized = false;
    private int nextId = 0;

    public EmbeddingService(AIEngineManager engineManager) {
        this.engineManager = engineManager;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void initialize() {
        initialized = true;
        PluginLogger.info("EmbeddingService initialized (dim=" + DIM + ", maxChunks=" + MAX_CHUNKS + ")");
    }

    public void shutdown() {
        initialized = false;
        store.clear();
    }

    // ── Indexing ──────────────────────────────────────────────────────────────

    /** Index all Java files in a project directory (called on project open). */
    public void indexProject(Path projectRoot) {
        if (!initialized) return;
        try {
            List<Path> javaFiles = Files.walk(projectRoot)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .limit(300)
                    .toList();

            for (Path f : javaFiles) indexFile(f);
            PluginLogger.info("Indexed " + javaFiles.size() + " files from " + projectRoot);
        } catch (Exception e) {
            PluginLogger.warn("Project indexing failed: " + e.getMessage());
        }
    }

    /** Index a single Java file. */
    public void indexFile(Path javaFile) {
        if (!initialized) return;
        try {
            String content = Files.readString(javaFile);
            splitIntoChunks(content, javaFile.toString())
                    .forEach(chunk -> addChunk(chunk, javaFile.toString()));
        } catch (Exception e) {
            PluginLogger.warn("Failed to index: " + javaFile.getFileName());
        }
    }

    // ── Retrieval ─────────────────────────────────────────────────────────────

    /**
     * Find the most similar code chunks for a query.
     * Returns up to TOP_K results above MIN_SIM threshold.
     */
    public List<CodeChunk> findSimilar(String query) {
        if (!initialized || store.isEmpty()) return Collections.emptyList();
        float[] qVec = computeEmbedding(query);

        rwLock.readLock().lock();
        try {
            return store.values().stream()
                    .map(chunk -> new ScoredChunk(chunk, cosineSimilarity(qVec, chunk.vector())))
                    .filter(s -> s.score() >= MIN_SIM)
                    .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                    .limit(TOP_K)
                    .map(ScoredChunk::chunk)
                    .toList();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Build a RAG context string for injection into AI prompts.
     */
    public String buildRAGContext(String query) {
        List<CodeChunk> similar = findSimilar(query);
        if (similar.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("## Related code from this project (use as style reference):\n\n");
        for (CodeChunk chunk : similar) {
            sb.append("// ").append(chunk.sourceFile()).append("\n");
            sb.append("```java\n").append(chunk.code()).append("\n```\n\n");
        }
        sb.append("## Current task:\n\n");
        return sb.toString();
    }

    public void clearIndex() {
        rwLock.writeLock().lock();
        try { store.clear(); }
        finally { rwLock.writeLock().unlock(); }
    }

    public int getIndexSize() { return store.size(); }

    // ── Embedding ─────────────────────────────────────────────────────────────

    /**
     * Hash-projection embedding (stub).
     * Replace with real sentence-transformer for semantic search quality.
     */
    private float[] computeEmbedding(String text) {
        float[] vec = new float[DIM];
        String[] tokens = text.toLowerCase().split("[^a-zA-Z0-9_]+");
        Set<String> seen = new HashSet<>();
        for (String tok : tokens) {
            if (tok.isBlank() || !seen.add(tok)) continue;
            int h = tok.hashCode();
            for (int d = 0; d < DIM; d++) {
                vec[d] += ((h ^ (h >>> d)) & 1) == 0 ? 1f : -1f;
            }
        }
        return l2Normalize(vec);
    }

    private float[] l2Normalize(float[] v) {
        float norm = 0;
        for (float x : v) norm += x * x;
        norm = (float) Math.sqrt(norm);
        if (norm < 1e-9f) return v;
        for (int i = 0; i < v.length; i++) v[i] /= norm;
        return v;
    }

    private float cosineSimilarity(float[] a, float[] b) {
        float dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na  += a[i] * a[i];
            nb  += b[i] * b[i];
        }
        float denom = (float)(Math.sqrt(na) * Math.sqrt(nb));
        return denom < 1e-9f ? 0f : dot / denom;
    }

    // ── Chunking ──────────────────────────────────────────────────────────────

    private List<String> splitIntoChunks(String source, String file) {
        List<String> chunks = new ArrayList<>();
        String[] lines = source.split("\n");
        StringBuilder current = new StringBuilder();
        int depth = 0;

        for (String line : lines) {
            current.append(line).append("\n");
            for (char c : line.toCharArray()) {
                if (c == '{') depth++;
                else if (c == '}') depth--;
            }
            // Flush at method boundary or max size
            if ((depth == 1 && current.length() > 200)
                    || current.length() > 1800) {
                String chunk = current.toString().trim();
                if (!chunk.isBlank()) chunks.add(chunk);
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) chunks.add(current.toString().trim());
        return chunks;
    }

    private void addChunk(String code, String sourceFile) {
        if (store.size() >= MAX_CHUNKS) return;
        String id = sourceFile + "#" + (nextId++);
        float[] vec = computeEmbedding(code);
        rwLock.writeLock().lock();
        try { store.put(id, new CodeChunk(id, code, sourceFile, vec)); }
        finally { rwLock.writeLock().unlock(); }
    }

    // ── Records ───────────────────────────────────────────────────────────────

    public record CodeChunk(String id, String code, String sourceFile, float[] vector) {}
    private record ScoredChunk(CodeChunk chunk, float score) {}
}
