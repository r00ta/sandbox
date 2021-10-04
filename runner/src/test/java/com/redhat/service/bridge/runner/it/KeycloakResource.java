package com.redhat.service.bridge.runner.it;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class KeycloakResource implements QuarkusTestResourceLifecycleManager {

    public static final String USER = "admin";
    public static final String PASSWORD = "admin";
    public static final String CLIENT_ID = "event-bridge";
    public static final String CLIENT_SECRET = "secret";
    public static final int PORT = 8080;
    private static final String REALM_FILE = "/tmp/realm.json";

    private final GenericContainer keycloak = new GenericContainer(DockerImageName.parse("jboss/keycloak:14.0.0"));

    public KeycloakResource() {
        keycloak.addExposedPort(PORT);
        keycloak.withEnv("KEYCLOAK_USER", USER);
        keycloak.withEnv("KEYCLOAK_PASSWORD", PASSWORD);
        keycloak.withEnv("KEYCLOAK_IMPORT", REALM_FILE);
        keycloak.withClasspathResourceMapping("testcontainers/keycloak/realm.json", REALM_FILE, BindMode.READ_ONLY);
        keycloak.waitingFor(Wait.forHttp("/auth").withStartupTimeout(Duration.ofMinutes(5)));
    }

    @Override
    public Map<String, String> start() {
        keycloak.start();
        String url = String.format("http://localhost:%s/auth/realms/event-bridge-fm", keycloak.getFirstMappedPort());
        return Collections.singletonMap("quarkus.oidc.auth-server-url", url);
    }

    @Override
    public void stop() {
        keycloak.close();
    }
}
