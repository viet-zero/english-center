# Copy this file to config/sepay.local.ps1 and fill the values locally.
# Load in the same PowerShell terminal used to run Spring Boot:
# . ./config/sepay.local.ps1
# ./mvnw.cmd spring-boot:run

$env:SEPAY_WEBHOOK_API_KEY = '' # API Key chosen for Authorization on the SePay webhook
$env:SEPAY_BANK = ''            # Bank code/BIN/name accepted by SePay QR, e.g. MBBank or VCB
$env:SEPAY_ACCOUNT_NUMBER = ''  # Receiving account selected in the SePay webhook filter
$env:SEPAY_DEMO = 'true'        # Set to false for bank-transfer QR and real webhook operation

# SePay Webhook settings:
# URL https://YOUR_PUBLIC_BACKEND/api/payments/sepay/webhook
# Event: money in. Format: JSON. Authentication: API Key.
# Use the same key as SEPAY_WEBHOOK_API_KEY. Select the receiving bank account above.
# Payment-code prefix EC, numeric suffix. Enable retries for non-2xx responses.
# The broad SePay API Token for querying transactions is not required by this integration.
