# DoorAlert

**An open door / trunk / hood alert for Android car head units.**

[![Build](https://github.com/mani-imani/DoorAlert/actions/workflows/build.yml/badge.svg)](https://github.com/mani-imani/DoorAlert/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/mani-imani/DoorAlert?label=release)](https://github.com/mani-imani/DoorAlert/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-black.svg)](LICENSE)

[فارسی](README.md)

![DoorAlert](docs/alert-1.png)

When a door, the trunk or the hood is left open, a large, glowing red card pops up in the middle of the screen and says what's open. It appears over whatever app is running (navigation, music, anything) without blocking it. Once everything is closed, it turns green and fades away.

| | |
|:---:|:---:|
| ![](docs/alert-2.png) | ![](docs/alert-ok.png) |

## Features

- Large red card with a pulsing glow, big text and a warning badge, easy to see at a glance
- Shows over any app while the app underneath stays usable
- Tap the card to dismiss it. It won't return until something else opens.
- Starts with the head unit
- **One-time self-learning setup:** the app learns which signal belongs to each door
- Demo alert and a raw CANBUS data view for troubleshooting

## Compatibility

| | |
|---|---|
| **Car** | JAC S3 |
| **Head unit** | ZQ8003 · board `ZQ_MTK_8007` · Android 9, with a CANBUS decoder |

The head unit needs a CANBUS decoder box and the vendor `car` service (`android.car.server`).

## Install & set up

1. Download `DoorAlert.apk` from the [latest release](https://github.com/mani-imani/DoorAlert/releases/latest) and install it.
2. Turn the ignition on and open DoorAlert. The setup wizard starts on its own:
   - Close everything and tap **Start**.
   - When asked, open **only that one** door, tap **Record**, then close it.
   - **Skip** anything your car has no sensor for (e.g. the hood).
3. Done. **Demo** shows the alert at any time.

If setup reports *no change detected*, open **Raw data**. If no line turns blue while you open and close a door, the CANBUS box isn't sending door state.

## How it works

- **Data:** the CANBUS box feeds the head unit's `car` service, which stores values under keys such as `canbus.state.door_data`. Every 250 ms DoorAlert reads all `canbus.*` keys with one `request.dm.get_value_by_prefix` call, parsing the reply straight off the binder Parcel ([`CarData.java`](app/src/main/java/ir/maniimani/dooralert/CarData.java)).
- **Learning:** every value is flattened into individual bits. The wizard samples the "all closed" state several times to rule out bits that flicker on their own. Then, for each door, it picks the bit that changes stably when only that door is open ([`Doors.java`](app/src/main/java/ir/maniimani/dooralert/Doors.java)).
- **Alert card:** a small centred window that passes all touches outside itself through to the app below ([`PopupActivity.java`](app/src/main/java/ir/maniimani/dooralert/PopupActivity.java)).

## Built with Claude

Built with **[Claude](https://claude.ai)** by Anthropic, on a **Claude Pro** plan. Claude worked through every stage: finding the CANBUS data in the head unit's system code, designing the self-learning setup, designing the alert card and writing the code. I did the real-car testing and made the design decisions.

## Author

**Mani ImaniSanjar** · [GeekMani](https://t.me/geekmani)
[maniimani.ir](https://maniimani.ir) · [GitHub](https://github.com/mani-imani) · [LinkedIn](https://linkedin.com/in/mani-imani)

## License

Code is released under the [MIT](LICENSE) license. The bundled [Vazirmatn](https://github.com/rastikerdar/vazirmatn) font is under the SIL OFL 1.1. This is an independent project, not affiliated with the head unit manufacturer or JAC Motors. Screenshots are UI previews.
