# Copy to config/payos.local.ps1, fill the three keys locally, then run:
# . .configpayos.local.ps1
# .mvnw.cmd spring-boot:run
$env:PAYOS_CLIENT_ID = ''       # Client ID from the payment channel
$env:PAYOS_API_KEY = ''         # API Key from the same payment channel
$env:PAYOS_CHECKSUM_KEY = ''    # Checksum Key from the same payment channel
$env:PAYOS_RETURN_URL = 'http://127.0.0.1:5175/'
$env:PAYOS_DEMO = 'true'        # Set to 'false' to use the real payment channel
# Register https://YOUR_PUBLIC_BACKEND/api/payments/payos/webhook on payOS.
