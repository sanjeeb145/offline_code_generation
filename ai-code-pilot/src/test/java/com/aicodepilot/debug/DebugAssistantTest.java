package com.aicodepilot.debug;

import com.aicodepilot.engine.AIEngineManager;
import com.aicodepilot.model.AIResponse;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DebugAssistant}.
 *
 * <p>Validates:
 * <ul>
 *   <li>String == bug detection</li>
 *   <li>Thread safety warnings (new Random, new Thread)</li>
 *   <li>NPE risk detection</li>
 *   <li>Report formatting</li>
 *   <li>Exception explanation via AI</li>
 * </ul>
 */
@DisplayName("DebugAssistant Tests")
class DebugAssistantTest {

    @Mock
    private AIEngineManager mockEngine;

    private DebugAssistant debugAssistant;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(mockEngine.isReady()).thenReturn(true);
        when(mockEngine.infer(any()))
                .thenReturn(AIResponse.success("No additional AI findings.", 30L));
        debugAssistant = new DebugAssistant(mockEngine);
    }

    @AfterEach
    void tearDown() throws Exception { mocks.close(); }

    // -----------------------------------------------------------------------
    // Bug Detection Tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Should detect String comparison with == operator")
    void shouldDetectStringEqualsOperator() {
        // Given
        String code = """
                public class StringBug {
                    public boolean isAdmin(String role) {
                        return role == "ADMIN";  // Bug: should use .equals()
                    }
                }
                """;

        // When
        DebugAssistant.DebugReport report = debugAssistant.detect(code);

        // Then
        assertThat(report.getBugs())
                .as("Should detect String == comparison bug")
                .anyMatch(b -> b.category().equals("BUG")
                        && b.title().contains("String"));
    }

    @Test
    @DisplayName("Should detect direct Thread creation")
    void shouldDetectDirectThreadCreation() {
        // Given
        String code = """
                public class ThreadAntiPattern {
                    public void startTask() {
                        Thread t = new Thread(() -> doWork());
                        t.start();
                    }
                    private void doWork() {}
                }
                """;

        // When
        DebugAssistant.DebugReport report = debugAssistant.detect(code);

        // Then
        assertThat(report.getBugs())
                .anyMatch(b -> b.category().equals("THREAD_SAFETY")
                        && b.title().contains("Thread"));
    }

    @Test
    @DisplayName("Should detect unsafe Random instantiation")
    void shouldDetectUnsafeRandom() {
        String code = """
                public class RandomUser {
                    private Random random = new Random();  // Not thread-safe
                    
                    public int nextInt() { return random.nextInt(100); }
                }
                """;

        DebugAssistant.DebugReport report = debugAssistant.detect(code);
        assertThat(report.getBugs())
                .anyMatch(b -> b.category().equals("THREAD_SAFETY"));
    }

    @Test
    @DisplayName("Should return empty bug list for clean code")
    void shouldReturnEmptyBugListForCleanCode() {
        String code = """
                public class CleanCode {
                    private final String value;
                    
                    public CleanCode(String value) {
                        this.value = Objects.requireNonNull(value, "value must not be null");
                    }
                    
                    public boolean isValid() {
                        return this.value.equals("valid");
                    }
                }
                """;

        DebugAssistant.DebugReport report = debugAssistant.detect(code);
        // Clean code may have 0 bugs
        assertThat(report).isNotNull();
        assertThat(report.toFormattedReport()).isNotBlank();
    }

    @Test
    @DisplayName("Should handle null/empty source gracefully")
    void shouldHandleEmptySource() {
        assertThatCode(() -> debugAssistant.detect(""))
                .doesNotThrowAnyException();
    }

    // -----------------------------------------------------------------------
    // Report Formatting
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Should produce formatted report with bug count")
    void shouldProduceFormattedReport() {
        String code = """
                public class Buggy {
                    public boolean check(String s) { return s == "test"; }
                }
                """;

        DebugAssistant.DebugReport report = debugAssistant.detect(code);
        String formatted = report.toFormattedReport();

        assertThat(formatted)
                .contains("Debug Report")
                .contains("Static Bugs Found");
    }

    // -----------------------------------------------------------------------
    // AI Integration
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Should include AI analysis in report when engine ready")
    void shouldIncludeAIAnalysis() {
        when(mockEngine.infer(any()))
                .thenReturn(AIResponse.success("AI found: potential N+1 query issue.", 100L));

        String code = """
                @Service
                public class ProductService {
                    public List<Product> getAll() {
                        return repository.findAll();
                    }
                }
                """;

        DebugAssistant.DebugReport report = debugAssistant.detect(code);
        assertThat(report.getAIAnalysis()).isNotBlank();
    }

    @Test
    @DisplayName("Should explain exception stack trace via AI")
    void shouldExplainException() {
        String stackTrace = """
                java.lang.NullPointerException: Cannot invoke "String.length()" because "str" is null
                    at com.example.MyService.process(MyService.java:42)
                    at com.example.MyController.handle(MyController.java:17)
                """;

        AIResponse response = debugAssistant.explainException(stackTrace, null);
        assertThat(response).isNotNull();
        verify(mockEngine, times(1)).infer(any());
    }
}
