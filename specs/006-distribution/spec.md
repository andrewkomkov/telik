# Feature Specification: Distribution channels

**Feature Branch**: `chore/distribution`
**Created**: 2026-11-03
**Status**: The parts without an account of the user are done. IzzyOnDroid, RuStore and the Downloader code wait for the user.
**Input**: The user asked for more stores for Telik, and to do all steps that need no action of the user.

## Findings

- Google Play: a rejection is likely. The remote uses an accessibility service, and Play allows it only to help users with disabilities.
- Amazon Appstore: Fire OS is Android 11 or older, and Telik needs Android 13 (`minSdk` 33). Telik cannot run there.
- RuStore takes Android TV apps since 2023. It needs a developer account of the user.
- IzzyOnDroid takes requests on Codeberg. It needs a Codeberg account of the user.
- The Downloader shortener (`go.aftvnews.com`) needs a captcha, so a person must create the code.

## Requirements

- **FR-001**: Each release MUST have the asset `telik-armv7.apk`, so the link to the latest release stays valid.
- **FR-002**: The READMEs MUST show the direct link and the Obtainium badge.
- **FR-003**: The site MUST have a privacy policy in English and Russian.
- **FR-004**: The folder `docs/stores` MUST hold the texts for the IzzyOnDroid request and the RuStore listing.
