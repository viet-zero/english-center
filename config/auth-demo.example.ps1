# Load this file in the same terminal used for Spring Boot:
# . ./config/auth-demo.example.ps1
$env:AUTH_EMAIL_DEMO = 'true'
$env:AUTH_RESET_BASE_URL = 'http://127.0.0.1:5175/'
# Admin can read simulated emails at Email khôi phục demo.
# AUTH_EMAIL_DEMO=false disables the simulated outbox. Real SMTP delivery is not configured.
