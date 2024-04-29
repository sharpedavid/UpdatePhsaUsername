# Keycloak PHSA Username Management

This repository contains two scripts designed to facilitate the management of usernames in the Keycloak PHSA realm. The
scripts aim to streamline the transition to the new username format and simplify client configuration by managing legacy
usernames.

## Purpose

1. **StorePhsaLegacyUsernameAsUserAttribute:**  
   This script migrates legacy usernames to a new attribute, `phsa_windowsaccountname`, in the Keycloak database. It
   prevents duplication and simplifies the process of aligning the usernames between PHSA AD and Entra ID.

2. **AddTerraformClientMapperForPhsaLegacyUsername:**  
   This script modifies Keycloak configurations to include custom mappings for legacy usernames, ensuring consistency
   across various applications.

## Notes

- **Light Documentation:** The scripts and this README file are only lightly documented because they are intended for a
  one-time use, after which they will be discarded. David Sharpe, the author of these scripts, will also handle running
  them within the next month or so.
- The scripts do not handle IDP configurations related to using UPN for account registration. This will need to be
  managed separately.
- Running the scripts has no immediate impact on the user experience until the manual IDP configuration step is
  completed later.
- The order of running the scripts does not matter.

## How to Use

### StorePhsaLegacyUsernameAsUserAttribute

1. **Service Account:** Create a service account in the target realm (`moh_applications`) with the necessary
   realm-management permissions, particularly `manage-users` and `view-users`.
2. **Modify Variables:** Adjust the variables at the top of the script file for the server URL, realm, client ID, and
   client secret. Load the secret as an environment variable; do not hardcode it.
3. **Run the Script:** Compile and run the script from within an IDE.
4. **Delete the Service Account**: Once the script has run successfully, delete the service account to prevent potential
   security risks.

### AddTerraformClientMapperForPhsaLegacyUsername

1. **Separate Checkout:** Ensure the Keycloak Terraform project is checked out separately.
2. **Setup:** Set the `baseDir` to your Keycloak Terraform folder. Set the `clientNamesFile` to correspond to your
   target environment. The `payaraTextFile` and `defaultTextFile` paths do not need to be modified, but make sure they
   are correct.
3. **Run the Script:** Run the script and review the results.
4. **Commit Changes:** Commit the changes in the Keycloak Terraform project.

### Timeline

The scripts are intended to be run in May 2024.