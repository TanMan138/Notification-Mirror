# Releasing

Tagging `v*` builds both apps and opens a **draft** GitHub release with the APK
and the zipped Mac app attached. Review the draft, then publish it.

```bash
git tag v1.0.0
git push origin v1.0.0
```

`workflow_dispatch` runs the same build without signing or publishing, which is
the way to check CI before cutting a tag.

## One-time setup

The Android signing key lives outside the repository. `android/release.jks` and
`android/keystore.properties` are gitignored; **back them up somewhere safe**.
Losing the key means future releases are signed with a different identity, and
anyone who installed an earlier build has to uninstall before they can update.

Add four repository secrets under Settings → Secrets and variables → Actions:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `base64 -i android/release.jks \| pbcopy` |
| `RELEASE_STORE_PASSWORD` | `storePassword` from `keystore.properties` |
| `RELEASE_KEY_ALIAS` | `notification-mirror` |
| `RELEASE_KEY_PASSWORD` | `keyPassword` from `keystore.properties` |

## Versioning

`versionName` comes from the tag with the leading `v` removed; `versionCode` is
the workflow run number, so it only ever increases. Nothing needs editing by
hand before a release.

## What the build guards against

The Android job fails rather than publishing if:

- the release APK is unsigned, or
- a Supabase project URL or publishable key is compiled into it.

The second guard matters because `local.properties` is baked into `BuildConfig`
at build time. Release builds force those fields empty, and the check confirms
it, so a maintainer's own project can never ship inside a public APK.

## The Mac app is not notarized

It is ad-hoc signed, which keeps the bundle intact but does not satisfy
Gatekeeper. First launch requires the steps in `macos/README.md`. Notarization
needs the $99/year Apple Developer Program; the project deliberately stays free
to maintain instead.
