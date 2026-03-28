package com.aicodepilot.devops;

import com.aicodepilot.engine.AIEngineManager;
import com.aicodepilot.model.AIRequest;
import com.aicodepilot.model.AIResponse;

/**
 * Generates DevOps artifacts using local AI inference.
 *
 * Supports:
 *  - Dockerfile (multi-stage Spring Boot)
 *  - Kubernetes manifests (Deployment, Service, HPA, PDB)
 *  - Kafka topic / consumer group configuration
 *  - Spring Boot application.yml with environment profiles
 *  - Docker Compose for local development
 */
public class DevOpsAssistant {

    private final AIEngineManager engineManager;

    public DevOpsAssistant(AIEngineManager engineManager) {
        this.engineManager = engineManager;
    }

    /** Generates a production-grade multi-stage Dockerfile. */
    public AIResponse generateDockerfile(String appName, String javaVersion) {
        String prompt = String.format("""
            Generate a production-ready multi-stage Dockerfile for Spring Boot app '%s'.
            Java version: %s

            Requirements:
            - Stage 1 (build): Use maven:3.9-eclipse-temurin-%s-alpine
            - Stage 2 (runtime): Use eclipse-temurin:%s-jre-alpine (minimal image)
            - Use Spring Boot layered JAR for optimal Docker cache layers
            - Run as non-root user (uid 1001, gid 1001)
            - HEALTHCHECK using /actuator/health endpoint
            - JVM flags: -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
            - EXPOSE 8080
            - Add .dockerignore content as a comment at the top
            - Add image labels (version, maintainer, build-date)

            Output ONLY the Dockerfile. No explanation.
            """, appName, javaVersion, javaVersion, javaVersion);

        return engineManager.infer(AIRequest.builder()
                .prompt(prompt)
                .systemPrompt("You are a DevOps engineer. Output only valid Dockerfile syntax.")
                .requestType(AIRequest.RequestType.GENERATE_DEVOPS)
                .maxNewTokens(800)
                .temperature(0.1f)
                .build());
    }

    /** Generates complete Kubernetes manifests. */
    public AIResponse generateKubernetesManifests(
            String appName, String namespace, int replicas) {
        String prompt = String.format("""
            Generate complete production Kubernetes manifests for Spring Boot app '%s'.

            Namespace: %s | Replicas: %d | Port: 8080

            Include (separated by ---):
            1. Namespace definition
            2. ConfigMap with Spring Boot overrides
            3. Secret template (with placeholder values)
            4. Deployment with:
               - Resource limits: CPU 500m/1000m, Memory 512Mi/1Gi
               - Liveness probe: /actuator/health/liveness (delay=30s)
               - Readiness probe: /actuator/health/readiness (delay=20s)
               - Pod anti-affinity (spread across nodes)
               - Rolling update: maxSurge=1, maxUnavailable=0
               - Environment from ConfigMap + Secret refs
            5. Service (ClusterIP, port 80→8080)
            6. HorizontalPodAutoscaler (min=%d, max=%d, CPU=70%%)
            7. PodDisruptionBudget (minAvailable=1)
            8. NetworkPolicy (allow only from ingress + same namespace)

            Output ONLY valid YAML.
            """, appName, namespace, replicas, replicas, replicas * 5);

        return engineManager.infer(AIRequest.builder()
                .prompt(prompt)
                .systemPrompt("You are a Kubernetes expert. Output only valid YAML.")
                .requestType(AIRequest.RequestType.GENERATE_DEVOPS)
                .maxNewTokens(1200)
                .temperature(0.05f)
                .build());
    }

    /** Generates Kafka topic + producer configuration. */
    public AIResponse generateKafkaConfig(String topicName, String appName) {
        String prompt = String.format("""
            Generate complete Kafka configuration for Spring Boot app '%s'.
            Topic: %s

            Include:
            1. application.yml section for Kafka producer
               - bootstrap-servers placeholder
               - serializer config (JSON)
               - acks=all for durability
               - retries + retry backoff
               - idempotence enabled
               - compression.type=snappy
            2. @Configuration class for KafkaProducerConfig
               - ProducerFactory bean
               - KafkaTemplate bean
               - Dead Letter Topic producer factory
            3. Topic definition @Bean with 3 partitions, replication-factor 2
            4. Sample domain event class (record)

            Output ONLY valid Java + YAML code blocks labeled clearly.
            """, appName, topicName);

        return engineManager.infer(AIRequest.builder()
                .prompt(prompt)
                .systemPrompt("You are a Kafka expert. Output valid Java and YAML.")
                .requestType(AIRequest.RequestType.GENERATE_DEVOPS)
                .maxNewTokens(900)
                .temperature(0.1f)
                .build());
    }

    /** Generates Docker Compose for local development. */
    public AIResponse generateDockerCompose(String appName, String[] services) {
        String serviceList = String.join(", ", services);
        String prompt = String.format("""
            Generate a docker-compose.yml for local development of '%s'.
            Required services: %s

            For each service include:
            - Correct official Docker image with pinned version tag
            - Health check
            - Named volume for data persistence
            - Environment variables
            - Port mappings

            Also include:
            - The Spring Boot app itself (built from ./Dockerfile)
            - depends_on with condition: service_healthy
            - A shared network

            Output ONLY valid docker-compose.yml (version 3.9).
            """, appName, serviceList);

        return engineManager.infer(AIRequest.builder()
                .prompt(prompt)
                .systemPrompt("You are a Docker expert. Output only valid YAML.")
                .requestType(AIRequest.RequestType.GENERATE_DEVOPS)
                .maxNewTokens(800)
                .temperature(0.1f)
                .build());
    }
}
