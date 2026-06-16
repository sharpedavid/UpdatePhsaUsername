import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.FederatedIdentityRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.ProcessingException;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;

public class StorePhsaLegacyUsernameAsUserAttribute {

    private static final Logger LOGGER = Logger.getLogger(StorePhsaLegacyUsernameAsUserAttribute.class.getName());

    static {
        LOGGER.setUseParentHandlers(false);
        LOGGER.setLevel(Level.INFO);
        Handler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.ALL);
        LOGGER.addHandler(consoleHandler);
    }

    private static boolean SIMULATION_MODE = true;
    private static final int PARALLELISM = Math.min(16, Math.max(1, Runtime.getRuntime().availableProcessors()));

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("--real")) {
            SIMULATION_MODE = false;
        }

        EnvConfig env = EnvConfig.PROD; // change or pass via args if you want
        Keycloak keycloak = authenticate(env);

        try {
            preflight(keycloak, env);
            updateUsers(keycloak, env);
        } finally {
            keycloak.close();
        }
    }

    private static void updateUsers(Keycloak keycloak, EnvConfig env) {
        int batchSize = 1000;
        int startIndex = 0;
        boolean moreUsers = true;
        ExecutorService executor = Executors.newFixedThreadPool(PARALLELISM);
        ConcurrentLinkedQueue<Keycloak> workerClients = new ConcurrentLinkedQueue<>();
        ThreadLocal<Keycloak> workerKeycloak = ThreadLocal.withInitial(() -> {
            Keycloak client = authenticate(env);
            workerClients.add(client);
            return client;
        });
        AtomicInteger usersChecked = new AtomicInteger();
        AtomicInteger usersUpdated = new AtomicInteger();

        LOGGER.info(() -> "Running in " + (SIMULATION_MODE ? "SIMULATION" : "REAL") +
                " mode against " + env.name() + " with " + PARALLELISM + " worker threads");

        try {
            while (moreUsers) {
                List<UserRepresentation> users = keycloak.realm(env.realm).users().list(startIndex, batchSize);

                if (users.isEmpty()) {
                    moreUsers = false;
                    continue;
                }

                final int from = startIndex;
                final int to = startIndex + users.size();
                LOGGER.fine(() -> "Processing users from " + from + " to " + to);

                List<Future<Boolean>> futures = new ArrayList<>(users.size());
                for (UserRepresentation user : users) {
                    futures.add(executor.submit(() -> processUser(workerKeycloak.get(), env, user)));
                }

                for (Future<Boolean> future : futures) {
                    try {
                        usersChecked.incrementAndGet();
                        if (future.get()) {
                            usersUpdated.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while processing users", e);
                    } catch (ExecutionException e) {
                        throw new RuntimeException("Failed to process user", e.getCause());
                    }
                }

                LOGGER.info(() -> "Processed " + usersChecked.get() + " users; " +
                        usersUpdated.get() + " users " + (SIMULATION_MODE ? "would be updated" : "updated"));

                startIndex += batchSize;
            }
        } finally {
            executor.shutdown();
            workerClients.forEach(Keycloak::close);
        }
    }

    private static boolean processUser(Keycloak keycloak, EnvConfig env, UserRepresentation user) {
        List<FederatedIdentityRepresentation> federatedIdentities =
                keycloak.realm(env.realm).users().get(user.getId()).getFederatedIdentity();

        Optional<FederatedIdentityRepresentation> phsaIdentity = federatedIdentities.stream()
                .filter(identity -> "phsa".equals(identity.getIdentityProvider()))
                .findFirst();

        boolean hasAttr = user.getAttributes() != null && user.getAttributes().containsKey("phsa_windowsaccountname");

        if (phsaIdentity.isPresent() && !hasAttr) {
            LOGGER.finer(() -> "PHSA user identified: " + user.getUsername());

            Map<String, List<String>> attributes =
                    Optional.ofNullable(user.getAttributes()).orElse(new HashMap<>());

            attributes.putIfAbsent("phsa_windowsaccountname", List.of(user.getUsername()));
            user.setAttributes(attributes);

            if (SIMULATION_MODE) {
                LOGGER.info(() -> "[SIMULATION] Would update user " + user.getUsername());
            } else {
                try {
                    keycloak.realm(env.realm).users().get(user.getId()).update(user);
                    LOGGER.info(() -> "[REAL] Updated user " + user.getUsername());
                } catch (BadRequestException e) {
                    LOGGER.log(Level.WARNING, "Skipping user rejected by Keycloak update. username={0}, id={1}, status={2}",
                            new Object[]{user.getUsername(), user.getId(), e.getResponse().getStatus()});
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static void preflight(Keycloak keycloak, EnvConfig env) {
        LOGGER.info("Running preflight checks...");
        try {
            keycloak.tokenManager().grantToken();
            keycloak.realm(env.realm).users().count();
        } catch (ProcessingException | NotAuthorizedException e) {
            throw new RuntimeException("Authentication failed in " + env.name(), e);
        } catch (ForbiddenException e) {
            throw new RuntimeException("Missing permissions in " + env.name(), e);
        }
        LOGGER.info("Preflight OK.");
    }

    private static Keycloak authenticate(EnvConfig env) {
        if (env.clientSecret.isEmpty()) {
            throw new IllegalStateException("Missing secret for " + env.clientId +
                    ". Set env var: " + env.clientId);
        }
        return KeycloakBuilder.builder()
                .serverUrl(env.url)
                .realm(env.realm)
                .clientId(env.clientId)
                .clientSecret(env.clientSecret)
                .grantType("client_credentials")
                .build();
    }

    enum EnvConfig {
        DEV("https://common-logon-dev.hlth.gov.bc.ca/auth", "moh_citizen", "BCMOHAD-30314-service-account"),
        TEST("https://common-logon-test.hlth.gov.bc.ca/auth", "moh_citizen", "BCMOHAD-31351-service-account"),
        PROD("https://common-logon.hlth.gov.bc.ca/auth", "moh_citizen", "BCMOHAD-31351-service-account");

        public final String url;
        public final String realm;
        public final String clientId;
        public final String clientSecret;

        EnvConfig(String url, String realm, String clientId) {
            this.url = url;
            this.realm = realm;
            this.clientId = clientId;
            this.clientSecret = System.getenv(clientId);
        }

        public boolean isProd() { return this == PROD; }
    }
}
