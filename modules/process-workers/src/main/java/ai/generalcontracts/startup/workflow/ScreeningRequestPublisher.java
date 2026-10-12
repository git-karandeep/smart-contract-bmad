package ai.generalcontracts.startup.workflow;

public interface ScreeningRequestPublisher {
    void publish(ScreeningRequestMessage request);
}
