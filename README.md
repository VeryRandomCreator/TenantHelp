# Rent Help
This program aims to help prevent rental property damage fraud for both tenants and landlords by cryptographically guarenteeing dates of property photos. This is my hackathon submission for Hack Canada 2026.

## The Problem: Dishonesty in Property Damage
The problem for both tenants and landlords is the potential for the other party to falsely claim damage was done during/outside of tenancy. While ordinary photos sent to the other party can act as a ceiling time for when the photo was taken, there is no system to prevent image doctoring, or grabbing older images found from other sources.
</p>
This app guarentees a ceiling time (not after) (through generating a bundle of signature, pdf, and certificate which the user can email to the other party), and a floor time (taking a photo in-app and immediately signing the latest blockchain hash to it) (not before).

### Why Blockchain Hash?
A pdf signed by a blockchain hash guarentees it was not generated before the claimed date. Mathematically, it is impossible to predict a future blockchain hash, meaning signing a pdf guarentees a timestamp floor. 

## How it works: Secure PDF Generation Process
### On Download: 
Github action builds app apk from the source code, and publishes the hash associated with the apk
    - Maintains open source principles and user security
    - Verification against the published hash guarentees only pdfs generated with the app with that signature are valid
### PDF Generation
1. App generates a hardware private and public key through Android TEE. (This matches the hash also available under the github action)
    - This is unique to the app and cannot be replicated
2. TEE is asked for an attestation certificate Chain.
    - Android generates a certificate that binds the apk's signature to the device
    - Also binds the latest blockchain hash to the certificate, which can be fetched in the verification process
3. PDF is signed using the TEE private key.
    - The pdf signature result is saved

Final bundle: PDF, PDF Signature, Certificate Chain

## How to Use
Go to Actions -> Latest (Update build.yml) -> Download rent-help.apk under "Artifacts". Then install APK and use!

## How to Verify
Visit the [Rent Help CLI Verification Tool](https://github.com/VeryRandomCreator/RentHelpCLI) for verification process.

## Future Improvements
- OTS (No need for gmail)

## AI Use Declaration
This hackathon submission is targeting the Google Antigravity track, so Google Antigravity and Gemini were heavily used during the development of this project. 

## Legal Disclaimer
Use at your own risk. I am not providing legal advice through this tool. 

Note: Disregard difference in app name vs repo (TenantHelp vs RentHelp). I wanted to change the overall name to RentHelp but was running out of time to change the Android details


