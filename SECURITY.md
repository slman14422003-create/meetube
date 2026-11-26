# Security

Report a vulnerability privately through **Security → Report a vulnerability** on this repository, not in a public issue.

How releases are protected:

- Every APK is built by GitHub Actions from this repository and published with `SHA256SUMS.txt`; check a download with `sha256sum -c SHA256SUMS.txt`.
- All APKs are signed with one key kept in repository secrets. Android refuses an update signed with a different key.
- Workflow actions are pinned to commit hashes, workflows run with read-only permissions, and only the release job can write.
- Dependabot and `update-components.yml` open weekly pull requests for newer dependencies and for the YouTube backend; each is built before it is offered.
