package com.aicodepilot.analyzer;

import com.aicodepilot.engine.AIEngineManager;
import com.aicodepilot.model.AIResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JavaCodeAnalyzer}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Role inference from annotations and naming conventions</li>
 *   <li>Anti-pattern detection (AST-based, no AI needed)</li>
 *   <li>Parse error handling</li>
 *   <li>AI analysis integration (mocked engine)</li>
 *   <li>Edge cases: empty input, syntax errors, very large files</li>
 * </ul>
 */
@DisplayName("JavaCodeAnalyzer Tests")
class JavaCodeAnalyzerTest {

    @Mock
    private AIEngineManager mockEngineManager;

    private JavaCodeAnalyzer analyzer;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(mockEngineManager.isReady()).thenReturn(true);
        when(mockEngineManager.infer(any()))
                .thenReturn(AIResponse.success("Mock AI analysis complete.", 50L));
        analyzer = new JavaCodeAnalyzer(mockEngineManager);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // -----------------------------------------------------------------------
    // Role Inference Tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Should infer Spring REST Controller from @RestController annotation")
    void shouldInferRestControllerRole() {
        // Given
        String code = """
                @RestController
                @RequestMapping("/api/products")
                public class ProductController {
                    @GetMapping("/{id}")
                    public ResponseEntity<ProductResponse> getById(@PathVariable Long id) {
                        return ResponseEntity.ok(service.findById(id));
                    }
                }
                """;

        // When
        AnalysisResult result = analyzer.analyze(code);

        // Then
        assertThat(result.getInferredRoles())
                .as("Should detect REST Controller role")
                .anyMatch(role -> role.contains("Controller"));
        assertThat(result.getClassName()).isEqualTo("ProductController");
    }

    @Test
    @DisplayName("Should infer Spring Service from @Service annotation")
    void shouldInferServiceRole() {
        // Given
        String code = """
                @Service
                @Transactional
                public class OrderService {
                    private final OrderRepository repository;
                    
                    public OrderService(OrderRepository repository) {
                        this.repository = repository;
                    }
                }
                """;

        // When
        AnalysisResult result = analyzer.analyze(code);

        // Then
        assertThat(result.getInferredRoles())
                .anyMatch(role -> role.contains("Service"));
    }

    @Test
    @DisplayName("Should infer JPA Entity from @Entity annotation")
    void shouldInferEntityRole() {
        String code = """
                @Entity
                @Table(name = "products")
                public class Product {
                    @Id
                    @GeneratedValue(strategy = GenerationType.IDENTITY)
                    private Long id;
                    private String name;
                }
                """;

        AnalysisResult result = analyzer.analyze(code);
        assertThat(result.getInferredRoles()).anyMatch(r -> r.contains("Entity"));
    }

    @Test
    @DisplayName("Should infer Kafka Consumer from @KafkaListener annotation")
    void shouldInferKafkaConsumerRole() {
        String code = """
                @Component
                public class OrderEventConsumer {
                    @KafkaListener(topics = "order-events", groupId = "order-service")
                    public void consume(OrderEvent event) {
                        log.info("Received: {}", event);
                    }
                }
                """;

        AnalysisResult result = analyzer.analyze(code);
        assertThat(result.getInferredRoles()).anyMatch(r -> r.contains("Kafka"));
    }

    // -----------------------------------------------------------------------
    // Anti-Pattern Detection Tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Should detect field injection anti-pattern")
    void shouldDetectFieldInjection() {
        // Given
        String code = """
                @Service
                public class UserService {
                    @Autowired
                    private UserRepository userRepository;  // BAD: field injection
                    
                    public User findById(Long id) {
                        return userRepository.findById(id).orElseThrow();
                    }
                }
                """;

        // When
        AnalysisResult result = analyzer.analyze(code);

        // Then
        assertThat(result.getStaticFindings())
                .as("Should flag @Autowired field injection")
                .anyMatch(f -> f.contains("Field injection") || f.contains("Autowired"));
    }

    @Test
    @DisplayName("Should detect methods with too many parameters")
    void shouldDetectTooManyParameters() {
        // Given
        String code = """
                public class OrderProcessor {
                    public Order processOrder(
                            String customerId, String productId, int quantity,
                            BigDecimal price, String currency, String address,
                            String couponCode) {
                        return new Order();
                    }
                }
                """;

        // When
        AnalysisResult result = analyzer.analyze(code);

        // Then
        assertThat(result.getStaticFindings())
                .as("Should flag method with too many parameters (7)")
                .anyMatch(f -> f.contains("parameters") || f.contains("parameter object"));
    }

    @Test
    @DisplayName("Should detect empty catch block")
    void shouldDetectEmptyCatchBlock() {
        String code = """
                public class FileProcessor {
                    public void process(String path) {
                        try {
                            Files.readString(Path.of(path));
                        } catch (IOException e) {
                            // Empty catch — swallows exception silently
                        }
                    }
                }
                """;

        AnalysisResult result = analyzer.analyze(code);
        assertThat(result.getStaticFindings())
                .anyMatch(f -> f.toLowerCase().contains("catch"));
    }

    @Test
    @DisplayName("Should detect public mutable field")
    void shouldDetectPublicMutableField() {
        String code = """
                public class Config {
                    public String serverUrl = "localhost";  // BAD: public mutable field
                    public static final int PORT = 8080;    // OK: public final static
                }
                """;

        AnalysisResult result = analyzer.analyze(code);
        assertThat(result.getStaticFindings())
                .anyMatch(f -> f.contains("mutable") || f.contains("encapsulation"));
    }

    // -----------------------------------------------------------------------
    // Parse Error Tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Should handle syntax errors gracefully")
    void shouldHandleSyntaxErrorsGracefully() {
        // Given: invalid Java with missing closing brace
        String malformedCode = """
                public class Broken {
                    public void method( {
                        // Missing closing paren — syntax error
                """;

        // When
        AnalysisResult result = analyzer.analyze(malformedCode);

        // Then — should not throw, should report parse errors
        assertThat(result).isNotNull();
        assertThat(result.getParseErrors()).isNotEmpty();
        assertThat(result.isStructuralAnalysisComplete()).isFalse();
    }

    @Test
    @DisplayName("Should handle empty input gracefully")
    void shouldHandleEmptyInput() {
        assertThatCode(() -> analyzer.analyze(""))
                .doesNotThrowAnyException();
    }

    // -----------------------------------------------------------------------
    // Clean Code Tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Should find no issues in clean, well-written code")
    void shouldFindNoIssuesInCleanCode() {
        // Given: well-written code following best practices
        String cleanCode = """
                @Service
                @Transactional(readOnly = true)
                public class ProductService {
                    
                    private final ProductRepository productRepository;
                    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
                    
                    public ProductService(ProductRepository productRepository) {
                        this.productRepository = productRepository;
                    }
                    
                    public Optional<Product> findById(Long id) {
                        log.debug("Finding product by id: {}", id);
                        return productRepository.findById(id);
                    }
                }
                """;

        // When
        AnalysisResult result = analyzer.analyze(cleanCode);

        // Then
        assertThat(result.getStaticFindings())
                .as("Well-written code should have no static findings")
                .isEmpty();
        assertThat(result.getInferredRoles()).contains("Spring Service");
    }

    // -----------------------------------------------------------------------
    // AI Integration Tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Should call AI engine when engine is ready")
    void shouldCallAIEngineWhenReady() {
        // Given
        when(mockEngineManager.isReady()).thenReturn(true);
        String code = "public class Test { public void method() {} }";

        // When
        analyzer.analyze(code);

        // Then
        verify(mockEngineManager, times(1)).infer(any());
    }

    @Test
    @DisplayName("Should skip AI analysis when engine is not ready")
    void shouldSkipAIAnalysisWhenEngineNotReady() {
        // Given
        when(mockEngineManager.isReady()).thenReturn(false);
        String code = "public class Test { public void method() {} }";

        // When
        AnalysisResult result = analyzer.analyze(code);

        // Then
        verify(mockEngineManager, never()).infer(any());
        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains("AI engine not ready"));
    }

    @Test
    @DisplayName("Should gracefully handle AI engine errors")
    void shouldHandleAIEngineErrors() {
        // Given
        when(mockEngineManager.infer(any()))
                .thenReturn(AIResponse.error("Model out of memory"));
        String code = "public class Test { public void method() {} }";

        // When
        AnalysisResult result = analyzer.analyze(code);

        // Then — should complete without throwing, with warning
        assertThat(result).isNotNull();
        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains("AI analysis failed"));
    }

    // -----------------------------------------------------------------------
    // AnalysisResult formatting
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Should produce non-empty formatted summary")
    void shouldProduceFormattedSummary() {
        String code = "@Service\npublic class TestService { }";
        AnalysisResult result = analyzer.analyze(code);

        String summary = result.toFormattedSummary();
        assertThat(summary)
                .isNotBlank()
                .contains("TestService")
                .contains("═"); // Header separator
    }
}
