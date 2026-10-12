package ai.generalcontracts.startup.workflow;

public interface ScreeningResultAuthenticator {
    boolean isAuthenticated(ScreeningResult result, String signature);
}
