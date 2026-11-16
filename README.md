# Fleet Tracker - Android app

Sends the phone location to your Fleet Tracker server every N minutes, even with the screen off.

## Get the APK (no Android Studio needed)
1. Create a free account on github.com, make a new private repository.
2. Upload ALL files of this folder (including the hidden .github folder) to it.
3. Open the Actions tab -> "Build APK" -> wait about 4 minutes.
4. Open the finished run -> Artifacts -> download FleetTracker-apk -> unzip -> app-debug.apk.
5. Send the APK to drivers (WhatsApp/Drive) and install it (allow "install unknown apps").

## Or with Android Studio
File -> Open this folder -> Build -> Build APK(s).

## On the phone
Enter server address (https://...), driver code, plate/unit ID, name, interval -> Start tracking.
Choose "Allow all the time" for location, and allow ignoring battery optimization.
