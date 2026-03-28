package com.aicodepilot.generator;

import com.aicodepilot.engine.AIEngineManager;
import com.aicodepilot.model.AIRequest;
import com.aicodepilot.model.AIResponse;
import com.aicodepilot.util.PromptTemplates;

import java.util.Map;

/**
 * Generates Java boilerplate code using the local AI engine.
 *
 * <p>Supported generation targets:
 * <ul>
 *   <li>Spring REST Controller (with CRUD endpoints)</li>
 *   <li>Service layer (interface + implementation)</li>
 *   <li>JPA Entity (with builder, equals/hashCode)</li>
 *   <li>DTO classes (Request/Response)</li>
 *   <li>Repository interface (JPA + custom queries)</li>
 *   <li>JUnit 5 test class</li>
 *   <li>Kafka producer/consumer</li>
 * </ul>
 *
 * <p>Generation uses a template-enriched prompting strategy: a concise
 * structural template is included in the prompt so the LLM fills in
 * business logic rather than learning the structure from scratch. This
 * dramatically improves output quality and reduces token usage.
 */
public class CodeGenerator {

    // -----------------------------------------------------------------------
    // Supported generation target types
    // -----------------------------------------------------------------------

    public enum GenerationTarget {
        CONTROLLER,
        SERVICE_INTERFACE,
        SERVICE_IMPL,
        ENTITY,
        DTO_REQUEST,
        DTO_RESPONSE,
        REPOSITORY,
        UNIT_TEST,
        KAFKA_PRODUCER,
        KAFKA_CONSUMER,
        DOCKERFILE,
        K8S_DEPLOYMENT
    }

    // -----------------------------------------------------------------------
    // Dependencies
    // -----------------------------------------------------------------------

    private final AIEngineManager engineManager;

    public CodeGenerator(AIEngineManager engineManager) {
        this.engineManager = engineManager;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Generates code for the specified target type.
     *
     * @param target     what to generate (e.g., CONTROLLER, SERVICE_IMPL)
     * @param entityName the domain entity name (e.g., "Product", "Order")
     * @param context    additional context (field names, method signatures, etc.)
     * @return AIResponse with the generated source code
     */
    public AIResponse generate(GenerationTarget target, String entityName,
                               Map<String, String> context) {
        String prompt = buildPrompt(target, entityName, context);
        String systemPrompt = PromptTemplates.CODE_GENERATION_SYSTEM_PROMPT;

        AIRequest request = AIRequest.builder()
                .prompt(prompt)
                .systemPrompt(systemPrompt)
                .requestType(AIRequest.RequestType.GENERATE_CODE)
                .maxNewTokens(900)  // Code generation needs more tokens
                .temperature(0.15f) // Near-deterministic for predictable code
                .build();

        return engineManager.infer(request);
    }

    /**
     * Generates code from a natural language description.
     * More flexible but requires a capable LLM.
     *
     * @param description plain English description of what to generate
     * @param packageName Java package to place the generated class in
     */
    public AIResponse generateFromDescription(String description, String packageName) {
        String prompt = String.format(
                "Generate production-ready Java code for the following requirement:\n\n"
                + "Package: %s\n"
                + "Requirement: %s\n\n"
                + "Include:\n"
                + "- Proper package declaration\n"
                + "- All necessary imports\n"
                + "- Javadoc comments\n"
                + "- Error handling\n"
                + "Output ONLY valid Java code in a single code block.",
                packageName, description);

        AIRequest request = AIRequest.builder()
                .prompt(prompt)
                .systemPrompt(PromptTemplates.CODE_GENERATION_SYSTEM_PROMPT)
                .requestType(AIRequest.RequestType.GENERATE_CODE)
                .maxNewTokens(1200)
                .temperature(0.2f)
                .build();

        return engineManager.infer(request);
    }

    /**
     * Generates REST endpoints from existing method signatures.
     * Useful when the service layer exists and you need to expose it via REST.
     *
     * @param serviceMethods list of method signatures from the service interface
     * @param entityName     domain entity name
     */
    public AIResponse generateRestEndpoints(String serviceMethods, String entityName) {
        String prompt = String.format(
                "Given the following Spring Service methods for '%s', "
                + "generate a complete Spring REST Controller with proper:\n"
                + "- @RestController and @RequestMapping annotations\n"
                + "- HTTP methods (GET, POST, PUT, DELETE)\n"
                + "- ResponseEntity return types\n"
                + "- @Valid validation annotations\n"
                + "- Exception handling with @ExceptionHandler\n"
                + "- OpenAPI @Operation annotations\n\n"
                + "Service methods:\n%s",
                entityName, serviceMethods);

        return engineManager.infer(AIRequest.builder()
                .prompt(prompt)
                .systemPrompt(PromptTemplates.CODE_GENERATION_SYSTEM_PROMPT)
                .requestType(AIRequest.RequestType.GENERATE_CODE)
                .maxNewTokens(1000)
                .temperature(0.1f)
                .build());
    }

    // -----------------------------------------------------------------------
    // Prompt construction — each target type has a specialized prompt
    // -----------------------------------------------------------------------

    private String buildPrompt(GenerationTarget target, String entityName,
                               Map<String, String> ctx) {
        return switch (target) {
            case CONTROLLER      -> buildControllerPrompt(entityName, ctx);
            case SERVICE_INTERFACE -> buildServiceInterfacePrompt(entityName, ctx);
            case SERVICE_IMPL    -> buildServiceImplPrompt(entityName, ctx);
            case ENTITY          -> buildEntityPrompt(entityName, ctx);
            case DTO_REQUEST     -> buildDTOPrompt(entityName, "Request", ctx);
            case DTO_RESPONSE    -> buildDTOPrompt(entityName, "Response", ctx);
            case REPOSITORY      -> buildRepositoryPrompt(entityName, ctx);
            case UNIT_TEST       -> buildUnitTestPrompt(entityName, ctx);
            case KAFKA_PRODUCER  -> buildKafkaProducerPrompt(entityName, ctx);
            case KAFKA_CONSUMER  -> buildKafkaConsumerPrompt(entityName, ctx);
            case DOCKERFILE      -> buildDockerfilePrompt(ctx);
            case K8S_DEPLOYMENT  -> buildK8sDeploymentPrompt(entityName, ctx);
        };
    }

    private String buildControllerPrompt(String entity, Map<String, String> ctx) {
        String pkg = ctx.getOrDefault("package", "com.example");
        String baseUrl = ctx.getOrDefault("baseUrl", entity.toLowerCase() + "s");
        return String.format("""
            Generate a production-ready Spring Boot REST Controller for the '%s' domain entity.
            
            Package: %s.controller
            Base URL: /api/v1/%s
            
            Include:
            - Full CRUD operations (GET all, GET by ID, POST, PUT, DELETE)
            - Pagination support for GET all (@PageableDefault)
            - Input validation with @Valid and @RequestBody
            - ResponseEntity<> with proper HTTP status codes
            - @ExceptionHandler for EntityNotFoundException and validation errors
            - Logging with SLF4J
            - OpenAPI/Swagger annotations (@Tag, @Operation, @ApiResponse)
            - Injecting %sService via constructor injection
            
            Output ONLY valid Java code.
            """, entity, pkg, baseUrl, entity);
    }

    private String buildEntityPrompt(String entity, Map<String, String> ctx) {
        String fields = ctx.getOrDefault("fields",
                "String name, String description, LocalDateTime createdAt");
        return String.format("""
            Generate a production-ready JPA Entity class for '%s'.
            
            Fields: %s
            
            Include:
            - @Entity, @Table(name = "%s")
            - @Id with @GeneratedValue(strategy = GenerationType.IDENTITY)
            - Hibernate Validator annotations (@NotNull, @NotBlank, @Size where appropriate)
            - @CreationTimestamp and @UpdateTimestamp for audit fields
            - Builder pattern (inner static Builder class)
            - equals() and hashCode() based on business key (NOT id)
            - toString() excluding lazy-loaded collections
            - No-arg constructor (JPA requirement)
            - All-args constructor
            
            Output ONLY valid Java code.
            """, entity, fields, entity.toLowerCase());
    }

    private String buildServiceInterfacePrompt(String entity, Map<String, String> ctx) {
        return String.format("""
            Generate a Spring Service interface for '%s' CRUD operations.
            
            Include methods:
            - Page<%sResponse> findAll(Pageable pageable)
            - %sResponse findById(Long id)
            - %sResponse create(%sRequest request)
            - %sResponse update(Long id, %sRequest request)
            - void delete(Long id)
            
            Use proper Javadoc for each method.
            Output ONLY valid Java code.
            """, entity, entity, entity, entity, entity, entity, entity);
    }

    private String buildServiceImplPrompt(String entity, Map<String, String> ctx) {
        return String.format("""
            Generate a Spring @Service implementation of %sService for '%s' entity.
            
            Include:
            - Constructor injection of %sRepository and ModelMapper
            - @Transactional on write methods
            - @Transactional(readOnly=true) on read methods
            - ResourceNotFoundException thrown when entity not found
            - SLF4J logging for key operations
            - Input validation before persistence
            
            Output ONLY valid Java code.
            """, entity, entity, entity);
    }

    private String buildDTOPrompt(String entity, String suffix, Map<String, String> ctx) {
        return String.format("""
            Generate a %s%s DTO class for the '%s' domain.
            
            Use Java Records if it's a Response DTO (immutable).
            Use a class with validation annotations if it's a Request DTO.
            
            Include:
            - Jakarta validation annotations (@NotNull, @NotBlank, @Email, @Size) for Request
            - Proper field names matching common conventions
            - Javadoc
            
            Output ONLY valid Java code.
            """, entity, suffix, entity);
    }

    private String buildRepositoryPrompt(String entity, Map<String, String> ctx) {
        return String.format("""
            Generate a Spring Data JPA Repository interface for '%s' entity.
            
            Extend JpaRepository<%s, Long> and JpaSpecificationExecutor<%s>.
            
            Include 3-5 relevant custom query methods:
            - A findBy method on a common field (name or email)
            - A findAll with @Query for a common filter scenario
            - A boolean existsBy method
            - @Modifying @Query for a soft-delete pattern
            
            Output ONLY valid Java code.
            """, entity, entity, entity);
    }

    private String buildUnitTestPrompt(String entity, Map<String, String> ctx) {
        String targetClass = ctx.getOrDefault("targetClass", entity + "Service");
        return String.format("""
            Generate a comprehensive JUnit 5 + Mockito test class for '%s'.
            
            Include:
            - @ExtendWith(MockitoExtension.class)
            - @Mock for all dependencies (%sRepository, etc.)
            - @InjectMocks for the service under test
            - Test methods for: happy path, entity not found, validation failure
            - AssertJ assertions (assertThat)
            - @DisplayName annotations for readable test names
            - Given/When/Then comment structure
            
            Output ONLY valid Java test code.
            """, targetClass, entity);
    }

    private String buildKafkaProducerPrompt(String entity, Map<String, String> ctx) {
        String topic = ctx.getOrDefault("topic", entity.toLowerCase() + "-events");
        return String.format("""
            Generate a Spring Kafka producer for '%s' domain events.
            
            Topic: %s
            
            Include:
            - @Service class with KafkaTemplate<String, %sEvent> injection
            - Send method with CompletableFuture handling
            - Success and failure callbacks with proper logging
            - Dead-letter topic configuration (DLT)
            - Message key strategy (use entity ID as key for ordering)
            - Proper error handling and retry logic
            
            Output ONLY valid Java code.
            """, entity, topic, entity);
    }

    private String buildKafkaConsumerPrompt(String entity, Map<String, String> ctx) {
        String topic = ctx.getOrDefault("topic", entity.toLowerCase() + "-events");
        String group = ctx.getOrDefault("groupId", "my-service-group");
        return String.format("""
            Generate a Spring Kafka consumer for '%s' domain events.
            
            Topic: %s
            Consumer Group: %s
            
            Include:
            - @KafkaListener with concurrency for parallel processing
            - @Payload deserialization with error handling
            - Idempotent processing logic (check if event already processed)
            - Dead letter queue handling with @DltHandler
            - Manual acknowledgment with Acknowledgment parameter
            - Proper SLF4J logging with MDC context
            
            Output ONLY valid Java code.
            """, entity, topic, group);
    }

    private String buildDockerfilePrompt(Map<String, String> ctx) {
        String appName = ctx.getOrDefault("appName", "my-app");
        String javaVersion = ctx.getOrDefault("javaVersion", "17");
        return String.format("""
            Generate a production-optimized multi-stage Dockerfile for a Spring Boot application.
            
            App name: %s
            Java version: %s
            
            Requirements:
            - Multi-stage build (build stage with Maven, runtime stage with JRE only)
            - Use eclipse-temurin:%s-jre-alpine as base image for minimal size
            - Run as non-root user (uid=1001)
            - Add HEALTHCHECK instruction
            - Use Spring Boot layered JAR extraction for optimal layer caching
            - Set JVM memory flags (-Xms256m -Xmx512m) as ENV
            - EXPOSE port 8080
            - Add metadata labels (maintainer, version)
            - .dockerignore patterns for Maven artifacts
            
            Output ONLY the Dockerfile content.
            """, appName, javaVersion, javaVersion);
    }

    private String buildK8sDeploymentPrompt(String appName, Map<String, String> ctx) {
        String namespace = ctx.getOrDefault("namespace", "default");
        String replicas  = ctx.getOrDefault("replicas", "2");
        return String.format("""
            Generate production-ready Kubernetes manifests for deploying '%s'.
            
            Namespace: %s
            Replicas: %s
            
            Include separate YAML documents for:
            1. Deployment with:
               - Resource requests/limits (CPU: 250m/500m, Memory: 256Mi/512Mi)
               - Liveness and readiness probes (/actuator/health)
               - Anti-affinity rules for HA across nodes
               - Rolling update strategy
               - Environment variables from ConfigMap and Secret refs
            2. Service (ClusterIP)
            3. HorizontalPodAutoscaler (min=2, max=10, CPU threshold=70%%)
            4. ConfigMap for application.properties overrides
            5. PodDisruptionBudget (minAvailable=1)
            
            Use --- separator between documents.
            Output ONLY valid YAML.
            """, appName, namespace, replicas);
    }
}
