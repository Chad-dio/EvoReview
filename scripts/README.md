# Scripts

Helper notes for local development.

## Forward GitHub webhooks to this machine

GitHub cannot call `localhost`. Use a tunnel (natapp or smee) and set the GitHub App Webhook URL to:

```text
http://YOUR_PUBLIC_HOST/api/github/webhook
```

See [docs/github-app.md](../docs/github-app.md).
