# soundcrowd-plugin-soundcloud

This soundcrowd plugin adds basic SoundCloud support. It allows you to browse your stream, likes, playlists, followings, followers, and your own music (requires SoundCloud account) and additionally supports searching for music. 

## Note

In order to use this plugin, you need your own SoundCloud API key. Update the `client_id` and `client_secret` resources in the `build.gradle` before building.

## Building

    $ git clone --recursive https://github.com/soundcrowd/soundcrowd-plugin-soundcloud
    $ cd soundcrowd-plugin-soundcloud
    $ ./gradlew assembleDebug

Install via ADB:

    $ adb install soundcloud/build/outputs/apk/debug/soundcloud-debug.apk

## License

Licensed under GPLv3.
