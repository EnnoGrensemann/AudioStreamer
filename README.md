# VinylCast

Android-App für das **Google Pixel 5**, um den Line-In-Sound eines Plattenspielers (über die **Cubilux USB-C Soundkarte**) live im lokalen WLAN auf eine **Sonos Playbar** zu streamen – inklusive manuellem Song-Recognition-Button (ACRCloud) und Album-Cover-Anzeige im Plattenspieler-Design.

## Features
- **USB-C Line-In Erfassung**: Erkennt angeschlossene USB-Audio-Interfaces (`AudioDeviceInfo.TYPE_USB_DEVICE` / `TYPE_USB_HEADSET`) automatisch und zeigt Live-Pegel auf einem Stereo-VU-Meter.
- **Lokaler HTTP-WAV-Stream**: Streamt 44.1 kHz / 16-Bit Stereo-PCM unkomprimiert über einen lokalen HTTP-Server im Pixel 5 direkt an die Sonos Playbar.
- **Sonos Playbar Steuerung**: Automatische SSDP-Erkennung im WLAN, Start/Stopp, Lautstärkeregelung und Übertragung von Titel/Artist/Cover-Metadaten an Sonos (`DIDL-Lite`).
- **Song-Erkennung auf Knopfdruck**: Nutzt einen 8-Sekunden-Ringpuffer und ACRCloud (inkl. Cover-Fallback über iTunes Search & MusicBrainz Cover Art Archive).
- **Plattenspieler-UI**: Dunkles Hi-Fi-Vinyl-Design mit animiertem Plattenteller, Tonarm, Cover-Label und Kiosk/Always-On-Display-Modus.

## ACRCloud einrichten
1. Kostenlosen Account auf [console.acrcloud.com](https://console.acrcloud.com) erstellen.
2. Ein Projekt vom Typ **Audio & Video Recognition** anlegen.
3. In der App unter **Einstellungen** den `Host` (z. B. `identify-eu-west-1.acrcloud.com`), `Access Key` und `Access Secret` eintragen.

## APK über GitHub Releases bauen
Der Workflow `.github/workflows/release.yml` baut automatisch eine Release-APK bei jedem Tag-Push (`v*`) oder manuell über **Actions → Run workflow**:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Optional können für eine eigene Release-Signatur folgende Repository-Secrets gesetzt werden:
- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Sind keine Secrets hinterlegt, nutzt der Build automatisch einen Debug-Signing-Fallback, sodass die APK direkt per Sideload auf dem Pixel 5 installiert werden kann.
