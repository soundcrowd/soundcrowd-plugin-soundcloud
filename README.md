# soundcrowd-plugin-soundcloud

[![android](https://github.com/soundcrowd/soundcrowd-plugin-soundcloud/actions/workflows/android.yml/badge.svg)](https://github.com/soundcrowd/soundcrowd-plugin-soundcloud/actions/workflows/android.yml)
[![GitHub release](https://img.shields.io/github/release/soundcrowd/soundcrowd-plugin-soundcloud.svg)](https://github.com/soundcrowd/soundcrowd-plugin-soundcloud/releases)
[![GitHub](https://img.shields.io/github/license/soundcrowd/soundcrowd-plugin-soundcloud.svg)](LICENSE)

This soundcrowd plugin adds basic SoundCloud support. It allows you to browse your stream, likes, playlists, followings, followers, and your own music (requires SoundCloud account) and additionally supports searching for music. 

## Note

In order to use this plugin, you need your own SoundCloud API key. Update the `client_id`, `client_secret` and `redirect_uri` resources in the `build.gradle` before building.

## Building

    $ git clone --recursive https://github.com/soundcrowd/soundcrowd-plugin-soundcloud
    $ cd soundcrowd-plugin-soundcloud
    $ ./gradlew assembleDebug

Install via ADB:

    $ adb install soundcloud/build/outputs/apk/debug/soundcloud-debug.apk

## License

Licensed under GPLv3.
