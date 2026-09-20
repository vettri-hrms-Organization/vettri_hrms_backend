# Vettri HRMS Email Setup

The backend sends existing transactional email through Zoho Mail SMTP using the shared `com.haodaone.recruitment.service.EmailService`.

## Zoho credentials

Use a Zoho app-specific password for SMTP authentication. Do not use the normal Zoho account password when app passwords are required. The app password is supplied through `ZOHO_MAIL_APP_PASSWORD` (or the compatible `MAIL_PASSWORD` fallback); it must never be committed, logged, or returned by an API.

## Local development

Copy `src/main/resources/application-local.properties.template` to `src/main/resources/application-local.properties` (the file is gitignored), then set the local database values and the following mail values:

```text
MAIL_HOST=smtp.zoho.in
MAIL_PORT=587
MAIL_USERNAME=customersupport@vettrihrms.in
ZOHO_MAIL_APP_PASSWORD=<ZOHO_MAIL_APP_PASSWORD>
MAIL_FROM_SYSTEM=noreply@vettrihrms.in
MAIL_FROM_BILLING=billing@vettrihrms.in
MAIL_FROM_SUPPORT=customersupport@vettrihrms.in
MAIL_FROM_SYSTEM_NAME=Vettri HRMS
MAIL_FROM_BILLING_NAME=Vettri Billing
MAIL_FROM_SUPPORT_NAME=Vettri Customer Support
```

Run from `Vettri_HRMS_Backend`:

```text
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The backend permits only these verified sender identities: `Vettri HRMS <noreply@vettrihrms.in>`, `Vettri Billing <billing@vettrihrms.in>`, and `Vettri Customer Support <customersupport@vettrihrms.in>`. Callers cannot choose an arbitrary From address.

## Production

Configure these environment variables on the backend service (Render/AWS or the selected production host):

```text
MAIL_HOST=smtp.zoho.in
MAIL_PORT=587
MAIL_USERNAME=customersupport@vettrihrms.in
ZOHO_MAIL_APP_PASSWORD=<ZOHO_MAIL_APP_PASSWORD>
MAIL_FROM_SYSTEM=noreply@vettrihrms.in
MAIL_FROM_BILLING=billing@vettrihrms.in
MAIL_FROM_SUPPORT=customersupport@vettrihrms.in
MAIL_FROM_SYSTEM_NAME=Vettri HRMS
MAIL_FROM_BILLING_NAME=Vettri Billing
MAIL_FROM_SUPPORT_NAME=Vettri Customer Support
```

`ZOHO_MAIL_APP_PASSWORD` is the only secret in this group. Store it in the host's secret environment-variable store, not in GitHub, `application.properties`, Docker images, or deployment logs. SMTP authentication and STARTTLS are enabled in `application.properties`, with 10-second connection, read, and write timeouts.

## Existing email features

The provider change connects the existing sender used by:

- Employee account invitations and resends
- Recruitment manager assignment and candidate interview notifications
- Signed offer-letter email with attachment
- Employee welcome/login email
- Agent-token OTP delivery
- Trial registration welcome and trial-start email
- Razorpay payment success and payment-failure email

The backend currently has no email-verification or password-reset token flow, asset/temporary-asset module, invoice generator, subscription scheduler, or contact/support-form endpoint. Razorpay payment verification exists; renewal, cancellation, reminder, and invoice flows do not. No mock or unauthenticated test-email endpoint was added.

Email delivery failures are logged without credentials and do not unnecessarily roll back the HRMS operation that triggered the notification. Offer-letter status continues to record the actual SMTP delivery result.
