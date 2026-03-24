package io.arachne.samples.domainseparation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sample.domain-separation")
public class DomainSeparationProperties {

    private final Model model = new Model();
    private final Runner runner = new Runner();
    private final DemoLogging demoLogging = new DemoLogging();

    public Model getModel() {
        return model;
    }

    public Runner getRunner() {
        return runner;
    }

    public DemoLogging getDemoLogging() {
        return demoLogging;
    }

    public static class Model {

        private String mode = "deterministic";

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }
    }

    public static class Runner {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class DemoLogging {

        private boolean enabled = true;
        private boolean verboseExecutor;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isVerboseExecutor() {
            return verboseExecutor;
        }

        public void setVerboseExecutor(boolean verboseExecutor) {
            this.verboseExecutor = verboseExecutor;
        }
    }
}