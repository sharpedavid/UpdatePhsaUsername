import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Scanner;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class AddTerraformClientMapperForPhsaLegacyUsername {

    // C:\Users\david.a.sharpe\IdeaProjects\moh-keycloak-client-configurations
    private static String baseDir = "C:\\Users\\david.a.sharpe\\IdeaProjects\\moh-keycloak-client-configurations\\keycloak-prod\\realms\\moh_applications\\clients";
    private static String clientNamesFile = "C:\\Users\\david.a.sharpe\\IdeaProjects\\UpdatePhsaUsername\\config\\prod_client_names.txt";
    private static String payaraTextFile = "C:\\Users\\david.a.sharpe\\IdeaProjects\\UpdatePhsaUsername\\config\\payara_append_block.txt";
    private static String defaultTextFile = "C:\\Users\\david.a.sharpe\\IdeaProjects\\UpdatePhsaUsername\\config\\append_block.txt";

    private static Logger LOGGER = Logger.getLogger(AddTerraformClientMapperForPhsaLegacyUsername.class.getName());

    static {
        LOGGER.setUseParentHandlers(false);
        LOGGER.setLevel(Level.FINE);
        Handler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.FINEST);
        LOGGER.addHandler(consoleHandler);
    }

    public static void main(String[] args) {
        String mode;
        if (args.length > 0) {
            mode = args[0];
        } else {
            System.out.println("No mode specified. Please enter 'apply' to append text or 'undo' to remove text:");
            Scanner scanner = new Scanner(System.in);
            mode = scanner.nextLine().trim();
            if (!(mode.equals("apply") || mode.equals("undo"))) {
                throw new IllegalArgumentException("Must specify either 'undo' or 'apply'");
            }
        }
        try {
            List<String> clientNames = Files.readAllLines(Paths.get(clientNamesFile));
            String payaraText = new String(Files.readAllBytes(Paths.get(payaraTextFile)));
            String defaultText = new String(Files.readAllBytes(Paths.get(defaultTextFile)));

            for (String clientName : clientNames) {
                Files.walk(Paths.get(baseDir))
                        .filter(path -> path.toString().endsWith(clientName + "\\main.tf"))

                        .forEach(path -> {
                            try {
                                String content = new String(Files.readAllBytes(path));
                                boolean containsModule = content.startsWith("module \"payara-client\"");
                                if ("apply".equals(mode)) {
                                    if (containsModule) {
                                        appendTextToFile(path, payaraText);
                                    } else {
                                        appendTextToFile(path, defaultText);
                                    }
                                } else if ("undo".equals(mode)) {
                                    if (containsModule) {
                                        removeTextFromFile(path, payaraText);
                                    } else {
                                        removeTextFromFile(path, defaultText);
                                    }
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            }

            LOGGER.log(Level.INFO, "Operation completed.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void appendTextToFile(Path filePath, String textToAppend) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            if (!content.contains(textToAppend)) {
                LOGGER.log(Level.FINE, "Appending to {0}", filePath);
                BufferedWriter writer = Files.newBufferedWriter(filePath, StandardOpenOption.APPEND);
                writer.write(textToAppend);
                writer.close();
            } else {
                LOGGER.log(Level.FINE, "The block already exists in {0}", filePath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not append text to file: " + filePath, e);
        }
    }

    private static void removeTextFromFile(Path filePath, String textToRemove) {
        try {
            LOGGER.log(Level.FINE, "Removing text from {0} if present", filePath);
            String content = new String(Files.readAllBytes(filePath));
            String updatedContent = content.replaceFirst(Pattern.quote(textToRemove), "");
            Files.write(filePath, updatedContent.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Could not remove text from: " + filePath, e);
        }
    }
}
