# yamibo-you

A third-party Android app for Yamibo 300, built around Material You / Material 3 UX.

> Unofficial project. Not affiliated with Yamibo.

## Current foundation

- Native Android: Kotlin + Jetpack Compose + Material 3
- Material You dynamic colors on Android 12+
- Dual-site model from day one:
  - Discuz BBS: `https://bbs.yamibo.com/`
  - New site: `https://www.yamibo.com/`
- Separate cookie jars for the BBS and new-site sessions
- Public-content loading with refresh/error/empty states
- Android CI definition for debug compilation

## BBS status

The Discuz X3.5 side is now native for the main read path:

```text
Forum index
   -> forum / thread list
      -> native thread reader
         -> posts / floors / author / time / image links
```

The BBS parser currently understands:

- forum IDs and forum pages
- normal and sticky thread rows
- thread tags, authors, replies, views and last-reply metadata
- Discuz post IDs, floors, authors and post time
- plain post text and embedded image URLs
- pagination
- `formhash` presence and fast-reply availability

BBS navigation stays inside Compose and preserves the forum -> thread back stack. Images currently open as source URLs while the image-loading/cache policy is still being designed.

## New-site status

The new site remains behind its own adapter. Its public novel landing page currently resolves to `/site/novel`, with work routes such as `/novel/<id>`. The next new-site step is to model editor recommendations, recent updates and ranking sections instead of presenting a flat link list.

## Architecture direction

```text
Compose UI
   |
   +-- BbsRepository ----------> bbs.yamibo.com (Discuz X3.5 HTML/session)
   |
   +-- YamiboRepository
          |
          +-- NewSiteAdapter --> www.yamibo.com

YamiboHttp
   +-- BBS CookieJar
   +-- New-site CookieJar
```

Discuz selectors are isolated from Compose models so an API can replace HTML parsing later without rewriting screens. Authentication state is also isolated per site because the forum and new site are separate web applications.

## Next milestones

1. Validate and harden the BBS selectors against more forums/threads.
2. Add persistent BBS session storage and native Discuz login.
3. Add native reply/post editor using Discuz `formhash`, then image upload.
4. Model new-site novel/comic feeds and add native work/reader screens.
5. Add account binding, favorites and history.
6. Add notifications/deep links and local caching.

## Development

Open the project with a recent Android Studio / JDK 17 environment. The current app targets Android API 35 and supports Android 8.0+ (API 26).

A GitHub Actions workflow is included to run `:app:assembleDebug`. If Actions are disabled for the repository, compilation still needs to be run manually in Android Studio or Gradle before a release build is considered verified.
