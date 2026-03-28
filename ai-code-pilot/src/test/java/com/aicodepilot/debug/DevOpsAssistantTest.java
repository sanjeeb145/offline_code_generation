package com.aicodepilot.debug;

import com.aicodepilot.devops.DevOpsAssistant;
import com.aicodepilot.engine.AIEngineManager;
import com.aicodepilot.model.AIResponse;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("DevOpsAssistant Tests")
class DevOpsAssistantTest {

    @Mock private AIEngineManager mockEngine;
    private DevOpsAssistant assistant;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(mockEngine.infer(any()))
                .thenReturn(AIResponse.success("FROM eclipse-temurin:17-jre-alpine\n...", 200L));
        assistant = new DevOpsAssistant(mockEngine);
    }

    @AfterEach
    void tearDown() throws Exception { mocks.close(); }

    @Test
    @DisplayName("Should generate Dockerfile")
    void shouldGenerateDockerfile() {
        AIResponse r = assistant.generateDockerfile("my-app", "17");
        assertThat(r.isSuccess()).isTrue();
        verify(mockEngine, times(1)).infer(
                argThat(req -> req.getPrompt().contains("Dockerfile")));
    }

    @Test
    @DisplayName("Should generate Kubernetes manifests")
    void shouldGenerateK8sManifests() {
        when(mockEngine.infer(any()))
                .thenReturn(AIResponse.success("apiVersion: apps/v1\nkind: Deployment", 300L));
        AIResponse r = assistant.generateKubernetesManifests("my-app", "production", 2);
        assertThat(r.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should generate Kafka configuration")
    void shouldGenerateKafkaConfig() {
        AIResponse r = assistant.generateKafkaConfig("order-events", "order-service");
        assertThat(r.isSuccess()).isTrue();
        verify(mockEngine).infer(argThat(req -> req.getPrompt().contains("Kafka")));
    }

    @Test
    @DisplayName("Should generate Docker Compose")
    void shouldGenerateDockerCompose() {
        AIResponse r = assistant.generateDockerCompose(
                "my-app", new String[]{"postgres", "kafka", "redis"});
        assertThat(r.isSuccess()).isTrue();
    }
}
