VIBE - MUSIC PLAYER (Java)
==========================
QUICK START
-----------
1. You need Java 8 or newer installed (check: java -version).
   Get it here if needed: https://adoptium.net
2. Run:
   - Windows: double-click run.bat
   - Linux/Mac: ./run.sh  (or: java -jar VibePlayer.jar)

EVERYTHING IS BUNDLED
---------------------
The JAR is "fat": the only external library (JLayer, for MP3 decoding)
is already packed inside. Nothing else to download.

FEATURES
--------
- 10-band equalizer (31 Hz - 16 kHz, +/-12 dB), real DSP engine,
  7 presets, on/off switch, settings are saved
- Lossless playback: WAV and AIFF play natively (no quality loss)
- MP3 playback via the bundled decoder
- Missing track covers are fetched automatically from the
  iTunes catalog and cached on disk
- Song lyrics from Genius (tab "Lyrics"; API token is already
  built into the app, nothing to enter)
- Playlist covers: from tags, from a file, or searched online
  (iTunes catalog, built in, no token needed)
- Playlist with cover art from tags, drag-and-drop files/folders,
  shuffle / repeat (off/all/one), volume, seek,
  playlist is saved between runs

DEVELOPMENT IN INTELLIJ IDEA
----------------------------
Open the project folder as a Maven project (pom.xml included) -
IntelliJ will auto-download all dependencies.
Run class: app.Main. Build your own JAR: mvn package
(The UI is built on Swing, so JavaFX is not required.)


MINIMAL MONOCHROME EDITION (2026-10)
------------------------------------
Theme re-styled: light monochrome palette (white surfaces, black accent,
hairline borders) per the minimalist player design. Sources updated in
src/app/Theme.java and src/app/UI.java; VibePlayer.jar rebuilt.
v2 (2026-10): gray monochrome palette; no placeholder squares for tracks
without cover; play indicator drawn as triangle (no missing glyphs);
tab focus ring removed.
v3 (2026-10): responsive seek bar + stop button; generated app icon; playlist search
by title/artist; lyrics via free lyrics.ovh source (Genius as fallback, better parsing);
lyrics auto-load when opening the tab or switching tracks.
