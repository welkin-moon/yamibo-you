# yamibo-you

A third-party Android app for Yamibo 300, built around Material You / Material 3 UX.

> Unofficial project. Not affiliated with Yamibo.

## Current foundation

- Native Android: Kotlin + Jetpack Compose + Material 3
- Material You dynamic colors on Android 12+
- Dual-site model from day one:
  - Discuz BBS: `https://bbs.yamibo.com/`
  - New site: `https://www.yamibo.com/`
- Site-specific HTML parsing hidden behind a common `SiteAdapter`
- Public-content MVP with refresh/error/empty states
- Browser handoff for detail pages until native detail rendering is implemented

## Architecture direction

```text
Compose UI
   |
   v
YamiboRepository
   |
   +-- DiscuzBbsAdapter ------> bbs.yamibo.com
   |
   +-- YamiboNewSiteAdapter --> www.yamibo.com
```

The UI consumes shared `ContentItem` models and must not depend on Discuz selectors or new-site DOM structure. This lets either source later move from HTML parsing to a real JSON endpoint without rewriting the UI.

## Next milestones

1. Improve BBS forum/thread parsing and native thread reader.
2. Inspect and stabilize new-site content endpoints/parsing.
3. Add separate cookie/session stores for BBS and new-site accounts.
4. Add login, favorites, history and account binding flows.
5. Add native post/reply editor, image upload and Discuz formhash handling.
6. Add notifications/deep links and local caching.

## Development

Open the project with a recent Android Studio / JDK 17 environment. The current app targets Android API 35 and supports Android 8.0+ (API 26).
