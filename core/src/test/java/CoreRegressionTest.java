import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class CoreRegressionTest {
    @Test
    void trackingConfigurationAndLayout() {
        assertDoesNotThrow(() -> CoreVerification.main(new String[0]));
    }
}
