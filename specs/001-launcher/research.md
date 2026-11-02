# Research Notes: Telik as the TV Launcher

**Status**: Desk research from 2026-11-02. No item here has a test on the target TV yet. Phase 0 in `tasks.md` tests each one.

<!-- STE exception: this file quotes sources and keeps their technical terms. -->

## Home role

- Android has no "default launcher" flag. The system sends an implicit intent with `ACTION_MAIN` and `CATEGORY_HOME`. The activity that answers it becomes Home. The choice "Always" stores a preferred activity for that intent. See [Big Nerd Ranch, Android Programming](https://www.oreilly.com/library/view/android-programming-the/9780135257555/ch23s05.html).
- Telik already declares `HOME` on an activity alias in its manifest. A test must show whether the system offers Telik as a Home app on the target TV.
- A forum post for Android TV 8 reports that the activity needs `HOME`, `LAUNCHER` and `DEFAULT`. It also describes disabling the stock launcher over adb with root. Root is out of scope for Telik. See [the post](https://developer.cloud.tencent.cn/article/1558729). The post is old, so treat it as unverified.
- Result for the plan: Telik can be a selectable Home app without root. Whether the TV maker locks the role is open until test T001.

## Content for the top shelf

- Apps publish preview channels and programs through `TvProvider` and need `WRITE_EPG_DATA`. See [Channels on the home screen](https://developer.android.com/training/tv/discovery/recommendations-channel).
- The AOSP `TvProvider` manifest marks `READ_EPG_DATA` as deprecated and no longer enforced. See [the manifest](https://android.googlesource.com/platform/packages/providers/TvProvider/+/android-o-preview-2/AndroidManifest.xml). Reading all channels needs `READ_TV_LISTINGS`, which is dangerous level and hidden in `TvContract`.
- Result for the plan: a third-party launcher might read preview programs with a normal query. This is not proven. Test T003 decides story 3. If the read fails, the shelf shows Telik content and the media session of the running app.

## Liquid glass on the TV

- `RenderEffect` works from API 31, and an AGSL `RuntimeShader` works from API 33. Telik has `minSdk` 33, so both are available with no fallback code for older versions.
- The usual method is a `RuntimeShader` applied with `graphicsLayer { renderEffect = ... }` and `RenderEffect.createRuntimeShaderEffect`.
- Small libraries exist for blur and liquid glass: [Cloudy](https://klibs.io/project/skydoves/Cloudy) and [AndroidLiquidGlass](https://github.com/Kashif-E/AndroidLiquidGlass). Both target phones. The plan does not add a library before test T006 shows a speed gain.
- No source gives frame time of AGSL glass on a low-end TV GPU. Only test T006 answers that. This is the main risk of the feature.

## Gaps

- The behavior of Home apps on Android 14 Google TV and of known third-party launchers is not documented in the sources that were found.
- Icon artwork: apps give one banner, not layered images. The depth effect comes from one image.
