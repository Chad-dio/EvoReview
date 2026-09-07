# GitHub App (Phase 1)

Current Stage: receive pull requests locally, fetch changed files, post a placeholder comment.

This is not LLM review yet.

## Local public URL (natapp / smee)

GitHub cannot call `localhost`. During development, expose Backend with a tunnel.

If you use natapp, GitHub App **Webhook URL** must be:

```text
http://YOUR_NATAPP_HOST/api/github/webhook
```

Example:

```text
http://t6436638.natappfree.cc/api/github/webhook
```

Keep natapp pointing at local port `8080`. Keep Backend running. Free natapp URLs often change after reconnect; update the GitHub App Webhook URL when that happens.

GitHub usually wants **HTTPS**. If HTTP is rejected, try:

```text
https://YOUR_NATAPP_HOST/api/github/webhook
```

Do not point the webhook at a production server until this Backend is deployed there.

## 1. GitHub App permissions

In the App settings:

- Repository permissions
  - **Pull requests**: Read & write
  - **Contents**: Read
- Subscribe to events
  - **Pull request**

Save, then install the App on a test repository.

Client ID (`Iv23...`) is not the App ID. The App ID is a number on the same settings page. JWT calls need the numeric App ID plus the `.pem` private key.

## 2. Local config

Put secrets in `backend/.local/application-local.yml` (gitignored):

- `app-id`: numeric App ID
- `client-id`: `Iv23...`
- `public-base-url`: tunnel origin, no path
- `webhook-secret`: same string as GitHub Webhook secret
- `private-key-path`: `.local/github-app.pem`

Download the private key from the GitHub App page to:

```text
backend/.local/github-app.pem
```

## 3. Run Backend

```bash
cd backend
mvnw.cmd spring-boot:run
```

Check:

```text
GET http://localhost:8080/api/stack
```

`github.configured` is `true` only when App ID, webhook secret, and the `.pem` file are all present.

## 4. Try a pull request

Open or update a PR in a repository where the App is installed.

GitHub → App → Advanced → Recent Deliveries should show `ping` and `pull_request` as 200.

The PR should get a placeholder comment listing changed files.
