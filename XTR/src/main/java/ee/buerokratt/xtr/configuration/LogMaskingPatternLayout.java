package ee.buerokratt.xtr.configuration;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Custom Logback layout that masks sensitive data patterns in log messages.
 * Intended to prevent sensitive data (like Estonian ID codes, API keys, etc.) 
 * from being persisted in log files.
 */
public class LogMaskingPatternLayout extends PatternLayout {

    private List<MaskingPattern> maskingPatterns = new ArrayList<>();

    /**
     * Initialize masking patterns for sensitive data.
     */
    public LogMaskingPatternLayout() {
        // Estonian Personal ID Code (11 digits starting with 3-6)
        addMaskingPattern("[3-6]\\d{10}", "[REDACTED_ID_CODE]");
        
        // Generic XML/JSON values that might contain sensitive data
        addMaskingPattern("<idCode>.*?</idCode>", "<idCode>[REDACTED]</idCode>");
        addMaskingPattern("<personalCode>.*?</personalCode>", "<personalCode>[REDACTED]</personalCode>");
        addMaskingPattern("\"idCode\"\\s*:\\s*\"[^\"]*\"", "\"idCode\":\"[REDACTED]\"");
        addMaskingPattern("\"personalCode\"\\s*:\\s*\"[^\"]*\"", "\"personalCode\":\"[REDACTED]\"");
        
        // Generic password/secret patterns
        addMaskingPattern("password=\\S+", "password=[REDACTED]");
        addMaskingPattern("\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"[REDACTED]\"");
        addMaskingPattern("apiKey=\\S+", "apiKey=[REDACTED]");
        addMaskingPattern("\"apiKey\"\\s*:\\s*\"[^\"]*\"", "\"apiKey\":\"[REDACTED]\"");
        
        // Authorization headers
        addMaskingPattern("Authorization:\\s*Bearer\\s+\\S+", "Authorization: Bearer [REDACTED]");
        addMaskingPattern("Authorization:\\s*Basic\\s+\\S+", "Authorization: Basic [REDACTED]");
    }

    /**
     * Add a pattern to mask in log messages.
     *
     * @param regex       Regular expression to match sensitive data
     * @param replacement Replacement text (e.g., "[REDACTED]")
     */
    private void addMaskingPattern(String regex, String replacement) {
        maskingPatterns.add(new MaskingPattern(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), replacement));
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        String message = super.doLayout(event);
        return maskSensitiveData(message);
    }

    /**
     * Apply all masking patterns to the message.
     *
     * @param message Original log message
     * @return Masked log message
     */
    private String maskSensitiveData(String message) {
        String maskedMessage = message;
        for (MaskingPattern pattern : maskingPatterns) {
            maskedMessage = pattern.mask(maskedMessage);
        }
        return maskedMessage;
    }

    /**
     * Inner class to hold pattern and replacement.
     */
    private static class MaskingPattern {
        private final Pattern pattern;
        private final String replacement;

        public MaskingPattern(Pattern pattern, String replacement) {
            this.pattern = pattern;
            this.replacement = replacement;
        }

        public String mask(String message) {
            Matcher matcher = pattern.matcher(message);
            return matcher.replaceAll(replacement);
        }
    }
}
