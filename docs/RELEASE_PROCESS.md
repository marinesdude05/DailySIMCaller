# Release process and direct install link

## Before producing APK

1. Read START HERE and BUILD STATUS. Ensure CI build and device tests are recorded.
2. Verify version name/code and code change summary, including any default-dialer role implications.
3. Sign release APK with an organization-controlled keystore. Store the keystore and passwords outside GitHub and outside public Drive links; maintain a protected backup.
4. Verify install and update of the same signing identity on one C9, then a few reset units. Keep a rollback plan.
5. Record SHA-256 hash, release version, compatible firmware, change log and tested device details in BUILD STATUS.

## Cloudflare hosting candidate (not configured yet)

- Upload the release APK to a private or appropriately scoped Cloudflare R2 location; expose it through an HTTPS download page/controlled endpoint.
- Use a short stable landing URL linking to a versioned APK. A version number and checksum prevent stale cached releases being mistaken for new ones.
- Installation without Google Play still needs Android's install confirmation / source permission for ordinary non-managed devices. Do not promise a completely silent installation.
- Keep test destinations and fleet information separate from APK public hosting.
- Record the URL only after the user has configured the destination and tested it; no URL has been provisioned yet.

## CI draft

The included `.github/workflows/android-ci.yml` runs a unit-test plus debug assembly build on pushes/PRs once the repository is linked. It does NOT sign a production release or deploy one. GitHub Actions results are pending; adjust SDK setup only if the first CI build provides evidence.
