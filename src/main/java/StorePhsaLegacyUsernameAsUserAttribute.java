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

    private static final String SERVER_URL = "https://common-logon-dev.hlth.gov.bc.ca/auth";
    private static final String REALM = "moh_applications";
    private static final String CLIENT_ID = "DAVIDSHARPE_19MAR24_DELETEME";
    private static final String CLIENT_SECRET_ENV = "DAVIDSHARPE_19MAR24_DELETEME";

    private static final Logger LOGGER = Logger.getLogger(StorePhsaLegacyUsernameAsUserAttribute.class.getName());

    static {
        LOGGER.setUseParentHandlers(false);
        LOGGER.setLevel(Level.FINER);
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
        List<UserRepresentation> users = keycloak.realm(REALM).users().list(1, 10_000);

        LOGGER.log(Level.FINER, "Found {0} users.", users.size());

        users.forEach(user -> {

            LOGGER.log(Level.FINEST, "Processing user: {0}.", user);

            List<FederatedIdentityRepresentation> federatedIdentities = keycloak.realm(REALM).users().get(user.getId()).getFederatedIdentity();

            Optional<FederatedIdentityRepresentation> phsaIdentity = federatedIdentities.stream()
                    .filter(identity -> "phsa".equals(identity.getIdentityProvider()))
                    .findFirst();

            if (phsaIdentity.isPresent() && (user.getAttributes() == null || !user.getAttributes().containsKey("phsa_windowsaccountname"))) {

                LOGGER.log(Level.FINER, "PHSA user identified: {0}.", user.getUsername());

                Map<String, List<String>> attributes = Optional.ofNullable(user.getAttributes()).orElse(new HashMap<>());
                attributes.putIfAbsent("phsa_windowsaccountname", List.of(user.getUsername()));
                user.setAttributes(attributes);
//                keycloak.realm(REALM).users().get(user.getId()).update(user);
            }
        });
    }

}
