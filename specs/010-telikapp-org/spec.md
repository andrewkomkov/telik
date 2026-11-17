# Feature Specification: The telikapp organization and the Product Hunt launch

**Feature Branch**: `chore/move-to-telikapp`

**Created**: 2026-11-17

**Status**: Done on 2026-11-17. The launch goes live on 2026-11-18.

**Input**: The user asked to launch Telik on Product Hunt and to move it to its own GitHub organization, as Gantry
(`getgantry`) and SecretBar (`secretbar`). The user chose the name `telikapp` and the first free launch day.

## Requirements

- **FR-001**: The repository MUST live at `telikapp/telik`. GitHub redirects the old links to the repository and the
  releases.
- **FR-002**: The site MUST live in its own repository `telikapp/telikapp.github.io` at `https://telikapp.github.io/`,
  as the site of Gantry. The `site/` folder and the Pages workflow leave this repository.
- **FR-003**: README, the F-Droid metadata and the store notes MUST use the new addresses.
- **FR-004**: The Product Hunt launch MUST point at `https://telikapp.github.io/`. The user confirms the submit and the
  date. The launch text states only the features that the user checked on the TV (spec 009 FR-001).

## Notes

- The old site `andrewkomkov.github.io/telik` stops after the transfer. GitHub Pages has no redirect for it.
