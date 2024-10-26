import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.FederatedIdentityRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StorePhsaLegacyUsernameAsUserAttribute {

    // PROD
    private static final String SERVER_URL = "https://common-logon.hlth.gov.bc.ca/auth";
    private static final String REALM = "moh_applications";
    private static final String CLIENT_ID = "DAVIDSHARPE_25SEPT24_PROD_DELETEME";
    private static final String CLIENT_SECRET_ENV = "DAVIDSHARPE_25SEPT24_PROD_DELETEME";

    // TEST
//    private static final String SERVER_URL = "https://common-logon-test.hlth.gov.bc.ca/auth";
//    private static final String REALM = "moh_applications";
//    private static final String CLIENT_ID = "DAVIDSHARPE_25SEPT24_DELETEME";
//    private static final String CLIENT_SECRET_ENV = "DAVIDSHARPE_25SEPT24_DELETEME";

    private static final Logger LOGGER = Logger.getLogger(StorePhsaLegacyUsernameAsUserAttribute.class.getName());

    static {
        LOGGER.setUseParentHandlers(false);
        LOGGER.setLevel(Level.FINEST);
        Handler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.FINEST);
        LOGGER.addHandler(consoleHandler);
    }

    public static void main(String[] args) {
        Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl(SERVER_URL)
                .realm(REALM)
                .grantType("client_credentials")
                .clientId(CLIENT_ID)
                .clientSecret(System.getenv(CLIENT_SECRET_ENV))
                .build();

        updateUsers(keycloak);
    }

    private static void updateUsers(Keycloak keycloak) {
        int batchSize = 1000;  // Number of users to retrieve at once
        int startIndex = 0;    // Index to start retrieving users from
        boolean moreUsers = true;

        // TODO: This takes hours to run if the user count is high. Consider parallelization.
        while (moreUsers) {
            List<UserRepresentation> users = keycloak.realm(REALM).users().list(startIndex, batchSize);

            LOGGER.log(Level.FINER, "Processing users from {0} to {1}.", new Object[]{startIndex, startIndex + batchSize});

            // Check if we have no more users
            if (users.isEmpty()) {
                moreUsers = false;
                continue;
            }

            users.forEach(user -> {

//                LOGGER.log(Level.FINEST, "Processing user: {0}, {1}.", new Object[]{user.getUsername(), user.getId()});

                List<FederatedIdentityRepresentation> federatedIdentities = keycloak.realm(REALM).users().get(user.getId()).getFederatedIdentity();

                Optional<FederatedIdentityRepresentation> phsaIdentity = federatedIdentities.stream()
                        .filter(identity -> "phsa".equals(identity.getIdentityProvider()))
                        .findFirst();

                if (phsaIdentity.isPresent() && (user.getAttributes() == null || !user.getAttributes().containsKey("phsa_windowsaccountname"))) {

                    LOGGER.log(Level.FINER, "PHSA user identified: {0}.", user.getUsername());

                    Map<String, List<String>> attributes = Optional.ofNullable(user.getAttributes()).orElse(new HashMap<>());
                    attributes.putIfAbsent("phsa_windowsaccountname", List.of(user.getUsername()));
                    user.setAttributes(attributes);
                    // Uncomment this line when you're ready to perform the update
                keycloak.realm(REALM).users().get(user.getId()).update(user);
                }
            });

            // Update startIndex for the next batch
            startIndex += batchSize;
        }
    }

}
