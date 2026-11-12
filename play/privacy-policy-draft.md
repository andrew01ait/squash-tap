# Keep Score privacy policy — draft for review

**Not ready for publication.** Confirm the publisher/legal entity, privacy contact, actual hosting/provider arrangements and retention/logging details before turning this draft into a public policy and adding in-app access. The landing page's short privacy explanation does not replace this release gate.

Publisher: **to be confirmed by the owner**. Privacy contact: **to be supplied by the owner**. Effective date: **set when the reviewed policy is published**.

## Local scorekeeping

Keep Score stores names, match scores, dates/times where known, results, rules, favourites and settings on your Wear OS watch. An account is not required. The app does not add advertising, analytics, GPS/heart-rate tracking or a permanent account-based cloud history.

Use the app's result/player controls and confirmed deletion actions to manage local records. Removing a player from future selection can retain their identity in old results. Uninstalling removes local app storage; exported files remain wherever you saved/shared them.

## Optional phone transfers and live scoreboards

When you choose a phone transfer, the app sends an encrypted snapshot to a temporary relay. Transfers may include saved results, names or a complete backup depending on your chosen action. Phone edits/imports return through an encrypted transfer and require watch review.

When you choose live sharing, the app sends the current match's score/rules/names/serving status and, where matching saved identities exist, a bounded head-to-head summary and latest previous meeting. It does not publish a searchable match list or your unrelated history.

Payload encryption keys are carried in the complete link's fragment and are needed to decrypt it. Anyone with the complete active link can open its contents; viewers may copy or retain what they see. Keep links private and share them only with the people you intend.

Phone transfer access lasts ten minutes. Live access lasts up to eight hours during play, with ten minutes for final results after completion. Explicit end/apply/stop actions request early closure when online. Access expiry is distinct from physical deletion: unused expired ciphertext is cleaned on subsequent relay traffic, not by a guaranteed exact-time erasure schedule. Review provider backup/log retention before finalizing this policy.

## Service metadata and providers

The relay needs session identifiers, hashed access capabilities, revisions, expiry/completion times and short-lived hashed-IP rate-limit records. The hosting infrastructure also receives network requests. Confirm and disclose its actual provider identity, request logging, retention, processing locations and contractual arrangements before publication; payload encryption alone does not settle these questions.

## Diagnostics, voice input and exports

Diagnostic details remain on the watch until you explicitly export/share them. They include bounded app lifecycle/error and whole-watch battery/device information. Voice entry, where available, uses the device's selected speech service; audit and explain that service handoff rather than claiming all speech is processed locally.

CSV, backup JSON, diagnostics and result-card images you download or share stay with their recipients until those recipients remove them. Keep Score cannot revoke such copies by expiring a link.

## Inquiries and changes

Insert the confirmed privacy contact and a usable inquiry/deletion process. Describe applicable user rights and the actual processor arrangements after review. State how policy changes will be communicated and keep the policy consistent with the app and Play Console declarations.
