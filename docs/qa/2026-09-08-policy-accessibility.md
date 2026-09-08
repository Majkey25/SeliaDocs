# Policy and accessibility checks, September 8, 2026

Scope: the GitHub Pages site, repository disclosures, and the app's existing policy entry points. No Android binary changed. The app's Privacy policy button opens `/privacy/`, which now links to every policy; Source code opens the repository README with the same links.

| Requested check | Result and limits |
| --- | --- |
| Refund policy | Added `/refunds/`. The app is free and has no paid tier. External contributions and Google Play transactions are distinguished; mandatory rights are not excluded. |
| Privacy policy | Updated `/privacy/` and `PRIVACY.md` with GitHub hosting, support correspondence, retention criteria, data rights, and contact routes. ML Kit metrics remain disclosed. |
| Terms and conditions | Added `/terms/` describing current free-app behavior, existing Apache license, content ownership, feature limits, support, and mandatory rights. No invented governing court or blanket consumer-rights waiver. |
| Cookies policy | Added `/cookies/`, separating static-site code, hosting request logs, external websites, and Android SDK metrics. |
| Form consent | No website forms or mailing-list signup exist. No dummy consent checkbox added. Support email is voluntary; the notice explains its purpose and discourages unnecessary personal data. |
| Local laws | Reviewed Czech ÚOOÚ cookie guidance, EU GDPR principles/grounds, and ČOI ADR information. Applicability depends on the publisher's legal identity, trader status, actual operations, and users' jurisdictions. This is not universal legal approval. |
| Cookie consent | No app-authored optional cookies, scripts, browser-storage code, or embeds were found on the site. No banner added. Optional tracking would need a fresh legal and consent review before loading. Hosting logs are disclosed, not described as zero data collection. |
| Necessary data | Site remains static with no collection form or new dependency. App policy links still open only after a user action. Google ML Kit SDK diagnostics remain; this pass does not certify that every SDK field is strictly necessary. |
| Tracking | No site analytics, pixels, remote fonts, or scripts. Existing app SDK metrics are explicitly distinguished from first-party analytics. No network packet capture or complete SDK privacy audit was performed. |
| Colour contrast | Fixed small header text from 4.17:1 to 4.85:1. Website text/link/button combinations checked against their backgrounds meet 4.5:1. Twelve explicit app-theme foreground/background pairs range from 5.28:1 to 15.18:1; this is not a screen-by-screen native audit. |
| Image alternatives | Each site image has an alt attribute. The repeated branding icon uses empty alt deliberately because the adjacent heading supplies its name. App notebook previews have content descriptions; user-created canvas content was not certified screen-reader equivalent. |
| Accessibility | Added skip links, visible 3 px focus outlines, labelled policy navigation, and wrapping links. All five pages have English language metadata, one main landmark, and one h1. |
| Button labels | Policy links, source/release link, contact, and license links have descriptive text. Existing native App details uses labelled Material buttons; its coffee icon is decorative inside a labelled button. |
| Keyboard-friendly forms | No web forms. Browser Tab reaches skip/privacy/terms/refunds/cookies in order, Enter opens Cookies, and the skip link focuses the content section. Full native TalkBack and hardware-keyboard form testing were not repeated. |
| Fake reviews | None found on the site. No testimonials, star ratings, or user-count claims added. |
| Third-party embeds | None found. Rendered resource elements are the local stylesheet and local icon. External services are ordinary user-activated links. |
| Image copyright | No new image added. Google Material attribution and third-party notices retained. Removed the unsupported blanket claim that branding is original. Source-art ownership/permissions still require publisher verification. |
| Business details | Verified public publisher name Majkey25 and support email majkeylab@gmail.com are shown. Legal name, postal/service address, registration/VAT details if applicable, and trader status were not supplied and were not invented. |
| Unsupported claims | Removed the broad “complete PDF export” marketing phrase and replaced it with concrete supported features and a link to known limits. Recognition/arithmetic limitations are stated. |

## Reproducible checks

`python .github/scripts/test_site.py` first failed on 4.17:1 contrast, then passed for five pages. It checks policy navigation, local links/fragments/assets, basic HTML semantics, and the site's no-script/no-form/no-embed constraints. It also runs before GitHub Pages deployment.

Rendered browser checks covered every page at 320, 768, 1024, and 1440 px. All 20 cases had no horizontal overflow against `documentElement.clientWidth`, loaded their images, and contained no forms, scripts, or embeds. Keyboard focus and skip navigation were exercised through browser key input. No browser console errors were returned for the checked tab. These checks are not a full WCAG conformance certification.

## Primary guidance reviewed

- [ÚOOÚ cookies guidance](https://uoou.gov.cz/verejnost/qa-otazky-a-odpovedi/cookies)
- [EU GDPR principles](https://commission.europa.eu/law/law-topic/data-protection/information-business-and-organisations/principles-gdpr_en)
- [EU legal grounds for processing](https://commission.europa.eu/law/law-topic/data-protection/information-business-and-organisations/legal-grounds-processing-data_en)
- [ČOI consumer dispute resolution](https://coi.gov.cz/pro-podnikatele/informace-pro-prodejce-zbozi-a-sluzeb/mimosoudni-reseni-spotrebitelskych-sporu-adr/)
- [W3C contrast guidance](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)
- [GitHub privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement)

Before claiming complete legal readiness, verify the publisher's legal/trader details, source-art rights, the operational handling of support data, and jurisdiction-specific SDK/privacy obligations. No new agreements, purchases, or Play declarations were accepted or changed.
