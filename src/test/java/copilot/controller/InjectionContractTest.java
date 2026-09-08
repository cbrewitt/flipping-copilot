package copilot.controller;

import com.google.inject.spi.InjectionPoint;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class InjectionContractTest {
    @Test
    public void portfolioControllersKeepInjectableConstructors() {
        for (Class<?> type : new Class<?>[]{PortfolioController.class, PortfolioBankTagController.class}) {
            InjectionPoint constructor = InjectionPoint.forConstructorOf(type);
            assertTrue("Missing injectable constructor: " + type, constructor.getDependencies().size() > 0);
        }
    }
}
