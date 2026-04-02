package com.aicodepilot.engine;

import com.aicodepilot.engine.llm.InferenceEngine;
import com.aicodepilot.model.AIRequest;
import com.aicodepilot.model.AIResponse;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AIEngineManager}.
 *
 * <p>Tests inference orchestration, caching, timeout, and error handling.
 */
@DisplayName("AIEngineManager Tests")
class AIEngineManagerTest {

    @Mock
    private InferenceEngine mockEngine;

    private AutoCloseable mocks;

    // A testable subclass that injects the mock engine
    private AIEngineManager createManagerWithMockEngine(InferenceEngine engine) {
        AIEngineManager mgr = new AIEngineManager(Path.of("/tmp/test-models")) {
            // Override to inject mock engine (production uses protected visibility)
        };
        // Use reflection to inject mock — in production, consider package-private test helper
        try {
            var field = AIEngineManager.class.getDeclaredField("activeEngine");
            field.setAccessible(true);
            field.set(mgr, engine);

            var readyField = AIEngineManager.class.getDeclaredField("ready");
            readyField.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicBoolean) readyField.get(mgr)).set(true);

            var typeField = AIEngineManager.class.getDeclaredField("engineType");
            typeField.setAccessible(true);
            typeField.set(mgr, "Mock Engine");
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mock engine", e);
        }
        return mgr;
    }

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(mockEngine.generate(anyString(), anyString(), anyInt(), anyFloat()))
                .thenReturn(AIResponse.success("Generated code result.", 100L));
    }

    @AfterEach
    void tearDown() throws Exception { mocks.close(); }

    // -----------------------------------------------------------------------
    // Inference Tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Should return successful response from engine")
    void shouldReturnSuccessfulResponse() {
        // Given
        AIEngineManager manager = createManagerWithMockEngine(mockEngine);
        AIRequest request = AIRequest.builder()
                .prompt("Analyze this code: public class Test {}")
                .requestType(AIRequest.RequestType.ANALYZE_CODE)
                .build();

        // When
        AIResponse response = manager.infer(request);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getText()).isEqualTo("Generated code result.");
    }

    @Test
    @DisplayName("Should cache identical prompts and not call engine twice")
    void shouldCacheIdenticalPrompts() {
        // Given
        AIEngineManager manager = createManagerWithMockEngine(mockEngine);
        AIRequest request = AIRequest.builder()
                .prompt("Same prompt for caching test")
                .build();

        // When — call twice with identical prompt
        AIResponse first  = manager.infer(request);
        AIResponse second = manager.infer(request);

        // Then — engine should only be called once
        verify(mockEngine, times(1)).generate(anyString(), anyString(), anyInt(), anyFloat());
        assertThat(second.isCacheHit()).isTrue();
    }

    @Test
    @DisplayName("Should throw when engine is not initialized")
    void shouldThrowWhenEngineNotInitialized() {
        // Given — manager that is NOT ready
        AIEngineManager manager = new AIEngineManager(Path.of("/tmp"));
        AIRequest request = AIRequest.builder().prompt("test").build();

        // Then
        assertThatThrownBy(() -> manager.infer(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not yet initialized");
    }

    @Test
    @DisplayName("Should return error response on engine exception")
    void shouldReturnErrorResponseOnEngineException() {
        // Given
        when(mockEngine.generate(anyString(), anyString(), anyInt(), anyFloat()))
                .thenThrow(new RuntimeException("Out of memory"));
        AIEngineManager manager = createManagerWithMockEngine(mockEngine);

        // When
        AIResponse response = manager.infer(AIRequest.builder().prompt("test").build());

        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Inference failed");
    }

    @Test
    @DisplayName("inferAsync should complete within timeout")
    void inferAsyncShouldComplete() throws Exception {
        // Given
        AIEngineManager manager = createManagerWithMockEngine(mockEngine);
        AIRequest request = AIRequest.builder().prompt("async test").build();

        // When
        CompletableFuture<AIResponse> future = manager.inferAsync(request);
        AIResponse response = future.get(5, TimeUnit.SECONDS);

        // Then
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("isReady should return false after shutdown")
    void isReadyShouldReturnFalseAfterShutdown() {
        AIEngineManager manager = createManagerWithMockEngine(mockEngine);
        assertThat(manager.isReady()).isTrue();

        manager.shutdown();
        assertThat(manager.isReady()).isFalse();
    }
}


