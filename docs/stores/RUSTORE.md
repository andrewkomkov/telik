# RuStore listing

RuStore takes Android TV apps in the developer console (https://console.rustore.ru) since 2023.
The developer needs a RuStore developer account. A person or a self-employed person can register.

## Steps

1. Sign in to the console.
2. Create the app with the package `dev.telik.tv`.
3. Select the device type "TV".
4. Upload the APK from the latest release: `telik-*-armv7.apk`. RuStore keeps the signature of the first upload, so use only APKs from the GitHub releases.
5. Fill in the fields below.
6. Send the app to moderation.

## Fields

| Field | Value |
|---|---|
| Name | Телик |
| Category | Инструменты |
| Age rating | 0+ |
| Price | Free |
| Privacy policy | https://andrewkomkov.github.io/telik/ru/privacy.html |
| Website | https://andrewkomkov.github.io/telik/ru/ |
| Support | https://github.com/andrewkomkov/telik/issues |
| Screenshots | `fastlane/metadata/android/en-US/images/tvScreenshots/` (1920x1080) |
| Short description | Copy `fastlane/metadata/android/ru/short_description.txt` |
| Full description | Copy `fastlane/metadata/android/ru/full_description.txt` |
| What is new | Copy the newest file in `fastlane/metadata/android/ru/changelogs/` |

## Notes for the moderation

```
Телик работает только на телевизоре. Он не собирает данные и не показывает рекламу.
Специальные возможности нажимают кнопки телевизора по командам пульта iPhone и вводят текст с клавиатуры iPhone.
Защищённое видео (DRM) по AirPlay не воспроизводится, Телик не обходит защиту.
Приложение не связано с Apple или Google.
```

## Open points

- RuStore may need more TV screenshots. Only one screenshot (`1_home.jpg`) exists now.
- Check the size and format rules of the console before the upload.
