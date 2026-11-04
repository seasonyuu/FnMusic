# Car projection through Shizuku

Open **我的 → 投屏到车机** after connecting the phone to the car. Start Shizuku,
grant FnMusic permission, choose a display, and tap **投屏**. Shizuku can be
started using [wireless debugging on the phone](https://shizuku.rikka.app/zh-hans/guide/setup/);
it usually needs to be started again after rebooting the phone.

Before authorization, the dialog only shows Shizuku setup or permission guidance.
The display chooser and projection controls appear automatically after permission
is granted.

The chooser reads the current system display list and prioritizes Jovi's
`vivo_car_screen` main display. It labels mirror and auxiliary screens separately;
**查看其他显示屏** reveals other external displays. Display IDs are discovered on
each connection and are never saved. Closed displays cannot be selected. If the
car disconnects, reconnect it and refresh the list.

FnMusic opens its existing interface on the chosen display using a Shizuku
UserService with shell privileges. The service exposes only launching FnMusic,
has a bounded command timeout, and stops after the request. This does not add
FnMusic to Jovi's supported-app list or modify Jovi's package or private files.
The phone's car connection must already provide an accessible external display.

For Jovi's home/bottom music card, enable FnMusic in **原子随身听 → 全部应用**
on the tested vivo setup. That media-card permission is separate from projection.

## Verification

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest --tests '*ProjectionTest'
```

Local tests cover changing display IDs, main-display ordering, stale connections,
closed displays, and launch-command failure responses. On a connected phone,
verify Shizuku stopped/unauthorized/authorized states, returning from Shizuku,
disconnecting/reconnecting the car while the chooser is open, opening FnMusic on
the selected main display, and touch/layout behavior there. No playback is needed.
Use in-place installation and follow [device data preservation](testing.md#device-checks).
