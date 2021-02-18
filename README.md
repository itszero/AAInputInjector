# AAInputInjector

![Image of AAInputInjector running with a head unit simulator. A helmet is on the side to illustrate the intended usage](./github/demo.jpg?raw=true "AAInputInjector Demo")

Here's a demo of what this is. In actual use, your phone will be wirelessly connected to the helmet-mounted HUD.

## What is this?

This is an app that enables you to control your Android Auto session through an on-screen controller from your phone.

## Um... Why?

Well, you see, I bought this [motorcycle helmet HUD](https://eye-lights.com/en) which comes with a single button controller which leaves me with no way to control the screen. (The official recommended way is to use voice command, however, mic reception can be less than ideal when you're out there riding.)

This apps gives me an alternative way to control the app running on the HUD.

## Okay, but how?

The helmet HUD presents itself as an Android Auto head unit. Supported phones would connect with it as if it is interacting with a car head unit. The input control is separated from your phone, meaning a Bluetooth connected controller won't interact with the Android Auto session.

This app bridges the gap by injecting code into the Android Auto using Xposed. Your phone will need to be rooted for this to work. The app also provides an overlay UI to present the controller. If you click on any of the buttons, the app would talk to the modified Android Auto app to inject a key event into it. The Android Auto system would then respond to it as if you pressed a physical button.

## Compatibility

This is built against Android Auto **v6.0.615334-release**. Any updates to the Android Auto can break this app.

## Safety

Don't get distracted from riding! Ride safely and ATGATT. :)

## LICENSE

MIT License