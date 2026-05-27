# Releasing the JetBrains Plugin

JetBrains releases use a release PR. The PR is where maintainers review and edit the version and changelog before anything is published.

## Create a Release PR

1. Open the GitHub Actions workflow:

[https://github.com/Kilo-Org/kilocode/actions/workflows/prepare-jetbrains-release.yml](https://github.com/Kilo-Org/kilocode/actions/workflows/prepare-jetbrains-release.yml)

2. Click **Run workflow**.

3. Fill the inputs:

| Input | Value |
|---|---|
| `kind` | `rc` for an EAP release, `stable` for a default Marketplace release. |
| `version` | `x.y.z-rc.n` for RCs, `x.y.z` for stable releases. |
| `from_tag` | Optional previous tag for the changelog range. Leave empty unless the default range is wrong. |

Examples:

```text
kind=rc
version=7.3.13-rc.1
```

```text
kind=stable
version=7.3.13
```

## Changelog Range Defaults

The workflow chooses a changelog base automatically:

| Release | Default `from_tag` |
|---|---|
| First RC for a version, e.g. `7.3.13-rc.1` | Latest stable JetBrains tag. |
| Later RC, e.g. `7.3.13-rc.2` | Previous RC for the same base version. |
| Stable, e.g. `7.3.13` | Latest stable JetBrains tag, ignoring RCs. |

Use `from_tag` only to override this comparison range.

## Review the PR

The workflow creates or updates a branch like:

```text
jetbrains/release/v7.3.13-rc.1
```

The PR updates:

| File | Purpose |
|---|---|
| `packages/kilo-jetbrains/package.json` | JetBrains plugin package version. |
| `packages/kilo-jetbrains/CHANGELOG.md` | Release notes packaged into the plugin. |

Review and edit `packages/kilo-jetbrains/CHANGELOG.md` before merging. This changelog entry is rendered into JetBrains `<change-notes>`, so it appears on the Marketplace and inside IntelliJ plugin UI.

## Merge and Publish

When the release PR is merged, the `tag-jetbrains-release` workflow validates it and creates:

```text
jetbrains/v<version>
```

That tag triggers the `publish-jetbrains` workflow:

[https://github.com/Kilo-Org/kilocode/actions/workflows/publish-jetbrains.yml](https://github.com/Kilo-Org/kilocode/actions/workflows/publish-jetbrains.yml)

Publishing behavior:

| Version | Marketplace channel | GitHub release |
|---|---|---|
| `x.y.z-rc.n` | `eap` | Prerelease |
| `x.y.z` | default | Stable release |

The workflow verifies, signs, and publishes the plugin ZIP, then uploads the ZIP to the matching GitHub Release.

## Installing RC Builds

RC builds are published to the `eap` channel. To get them in IntelliJ IDEA:

1. Open **Settings > Plugins**.
2. Click the gear icon and choose **Manage Plugin Repositories**.
3. Add the following URL:

```text
https://plugins.jetbrains.com/plugins/list?channel=eap&pluginId=28350
```

4. Search for **Kilo Code** in the Marketplace tab.

## Manual Recovery

If the PR was merged but the tag workflow failed after validation, create the tag manually at the merge commit:

```bash
git fetch origin main
git tag jetbrains/v7.3.13 <merge-sha>
git push origin jetbrains/v7.3.13
```

Do not create a tag before the release PR is merged unless intentionally bypassing the release-PR flow.

## Required GitHub Actions Secrets

| Secret | Purpose |
|---|---|
| `KILO_MAINTAINER_APP_ID` | GitHub App ID used to create/update release PRs and tags. |
| `KILO_MAINTAINER_APP_SECRET` | GitHub App private key used to create/update release PRs and tags. |
| `JETBRAINS_MARKETPLACE_TOKEN` | Marketplace API token for publishing. |
| `JETBRAINS_CERTIFICATE_CHAIN` | PEM certificate chain for plugin signing. |
| `JETBRAINS_PRIVATE_KEY` | PEM private key for plugin signing. |
| `JETBRAINS_PRIVATE_KEY_PASSWORD` | Password for the private key. |

Before the first publish, complete `RELEASE_TODO.md` to set up these secrets and the Marketplace plugin entry.
