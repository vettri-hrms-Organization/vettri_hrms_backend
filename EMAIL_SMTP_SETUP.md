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
MAIL_FROM=customersupport@vettrihrms.in
MAIL_FROM_NAME=Vettri Customer Support
```

Run from `Vettri_HRMS_Backend`:

```text
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The sender is fixed by backend configuration as `Vettri Customer Support <customersupport@vettrihrms.in>`. A recipient may be validated and an optional Reply-To may be supplied by future internal callers, but callers cannot choose the From address.

## Production

Configure these environment variables on the backend service (Render/AWS or the selected production host):

```text
MAIL_HOST=smtp.zoho.in
MAIL_PORT=587
MAIL_USERNAME=customersupport@vettrihrms.in
ZOHO_MAIL_APP_PASSWORD=<ZOHO_MAIL_APP_PASSWORD>
MAIL_FROM=customersupport@vettrihrms.in
MAIL_FROM_NAME=Vettri Customer Support
```

`ZOHO_MAIL_APP_PASSWORD` is the only secret in this group. Store it in the host's secret environment-variable store, not in GitHub, `application.properties`, Docker images, or deployment logs. SMTP authentication and STARTTLS are enabled in `application.properties`, with 10-second connection, read, and write timeouts.

## Existing email features

The provider change connects the existing sender used by:

- Employee account invitations and resends
- Recruitment manager assignment and candidate interview notifications
- Signed offer-letter email with attachment
- Employee welcome/login email
- Agent-token OTP delivery

The backend currently has no email trigger for password reset, signup verification, asset expiry, temporary asset, service billing, invoice, or contact/support-form notifications. No new business flows were invented for this provider change. There is also no public test-email endpoint.

Email delivery failures are logged without credentials and do not unnecessarily roll back the HRMS operation that triggered the notification. Offer-letter status continues to record the actual SMTP delivery result.
