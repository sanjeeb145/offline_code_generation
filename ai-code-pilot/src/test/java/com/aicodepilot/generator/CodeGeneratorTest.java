package com.aicodepilot.generator;

import com.aicodepilot.engine.AIEngineManager;
import com.aicodepilot.model.AIResponse;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CodeGenerator}.
 * Validates prompt construction and engine delegation for all target types.
 */
@DisplayName("CodeGenerator Tests")
class CodeGeneratorTest {

    @Mock
    private AIEngineManager mockEngine;

    private CodeGenerator generator;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(mockEngine.infer(any()))
                .thenReturn(AIResponse.success("public class ProductController { }", 150L));
        generator = new CodeGenerator(mockEngine);
    }

    @AfterEach
    void tearDown() throws Exception { mocks.close(); }

    @Test
    @DisplayName("Should generate controller and return success response")
    void shouldGenerateController() {
        AIResponse response = generator.generate(
                CodeGenerator.GenerationTarget.CONTROLLER,
                "Product",
                Map.of("package", "com.example"));

        assertThat(response.isSuccess()).isTrue();
        verify(mockEngine, times(1)).infer(any());
    }

    @Test
    @DisplayName("Should generate Dockerfile")
    void shouldGenerateDockerfile() {
        when(mockEngine.infer(any()))
                .thenReturn(AIResponse.success("FROM eclipse-temurin:17-jre-alpine", 200L));

        AIResponse response = generator.generate(
                CodeGenerator.GenerationTarget.DOCKERFILE,
                "my-app",
                Map.of("javaVersion", "17"));

        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should generate K8s deployment")
    void shouldGenerateK8sDeployment() {
        when(mockEngine.infer(any()))
                .thenReturn(AIResponse.success("apiVersion: apps/v1\nkind: Deployment", 300L));

        AIResponse response = generator.generate(
                CodeGenerator.GenerationTarget.K8S_DEPLOYMENT,
                "product-service",
                Map.of("namespace", "production", "replicas", "3"));

        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("generateFromDescription should pass description to engine")
    void shouldGenerateFromDescription() {
        generator.generateFromDescription(
                "A service that calculates order totals with discount logic",
                "com.example.service");

        verify(mockEngine, times(1)).infer(
                argThat(req -> req.getPrompt().contains("discount logic")));
    }

    @Test
    @DisplayName("generateRestEndpoints should include service methods in prompt")
    void shouldGenerateRestEndpointsFromServiceMethods() {
        String methods = "List<Product> findAll(Pageable p);\nProduct findById(Long id);";
        generator.generateRestEndpoints(methods, "Product");

        verify(mockEngine, times(1)).infer(
                argThat(req -> req.getPrompt().contains("findAll")));
    }

    @Test
    @DisplayName("Should propagate engine errors")
    void shouldPropagateEngineErrors() {
        when(mockEngine.infer(any()))
                .thenReturn(AIResponse.error("Model context overflow"));

        AIResponse response = generator.generate(
                CodeGenerator.GenerationTarget.ENTITY, "Order", Map.of());

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("context overflow");
    }

    @Test
    @DisplayName("All target types should execute without throwing")
    void shouldSupportAllTargetTypes() {
        for (CodeGenerator.GenerationTarget target : CodeGenerator.GenerationTarget.values()) {
            assertThatCode(() -> generator.generate(target, "TestEntity", Map.of()))
                    .as("Target " + target + " should not throw")
                    .doesNotThrowAnyException();
        }
    }
}
