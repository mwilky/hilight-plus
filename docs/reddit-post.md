I've been working on an app for the Pixel 11 Pro's rear LED ring and it's now on Google Play. Out of the box the ring only does two things: calls from favourites and Gemini's listening/thinking/replying lights. HiLight Plus takes it over so you can decide what it shows.

The short version: a colour and pattern for each contact or app, a charging gauge, a low battery warning, a Do Not Disturb indicator, and rules for when it should stay dark. It can also redo Gemini's lights in whatever look you want.

And best of all, Shizuku isn't required!

[Google Play](https://play.google.com/store/apps/details?id=com.mwilky.hilight.plus) · [Source on GitHub](https://github.com/mwilky/hilight-plus) · [Screenshots](https://github.com/mwilky/hilight-plus#readme)

7-day trial with everything unlocked, then a one-off purchase. No subscription.

**Calls**

* Pick a colour and pattern per contact. Separate defaults for everyone else in your contacts and for unknown numbers.
* Starred contacts can share a favourites style if they don't have their own rule.
* Works for normal calls and app calls (WhatsApp, Teams, Meet and so on).
* Stays lit until you answer or reject. Optionally keeps going for a missed call until you clear the notification.

**Notifications**

* Rules per sender and per app. App rules can pull the colour from the app icon.
* 13 patterns in three groups: Simple, Motion and Gemini. Solid, Breathe, Pulse, Wave, Comet, Orbit, Beacon, Ripple, Sparkle, Rainbow, plus Gemini's own Listening, Thinking and Replying effects.
* Eight preset colours or anything from a colour wheel.
* Brightness from a dim glow to full, and speed from half to double the pattern's normal pace, per rule.
* Patterns run as looping effects on the ring's own light controller, the same way stock Gemini does it, so they're smooth and the app isn't redrawing the ring many times a second.
* When several notifications are waiting you can show the newest only, take turns through them, or split the ring into an arc per notification (up to four). Split arcs can breathe, stay solid, spotlight in turn or rotate round the ring, and each new notification can optionally play its own pattern once before the arcs come back.
* Lights go off the moment the notification is cleared from the shade.

**Battery**

* Charging gauge, fill animation or gradient ring while plugged in.
* Red to green by charge level, or a fixed colour.
* Full charge indicator with a timeout.
* Low battery warning at whatever level you set.
* Battery can sit under notifications or override them.

**Gemini**

* Replaces the stock Gemini feedback with your own pattern and colour for listening, thinking and replying. Defaults match the stock look.
* Each state can be turned off or given its own conditions.
* This works by following Gemini's own log on the phone, so a Gemini update could break it until I catch up.

**Do Not Disturb**

* Lights the ring while Do Not Disturb is on, with its own colour, brightness and speed.
* Six calm patterns just for this: DND sign (the 3 and 9 o'clock LEDs), Crescent, Sleep, Snooze, Glance and Drift.
* Show it the whole time DND is on, or just for the first 10 seconds, 1 minute or 5 minutes.
* Optional sweep round the ring as DND turns on, and backwards as it turns off.

**Conditions**

* Face down only. The accelerometer only runs while something is waiting to light.
* Stay dark during Do Not Disturb.
* Quiet hours, with per-rule overrides.
* Pocket mode, on by default. No glowing through your jeans: the ring waits until the phone's out of your pocket. Face down on a table doesn't count, so face-down rules still work.

**Pause**

* A Quick Settings tile pauses everything, or just calls, notifications, battery, Gemini or Do Not Disturb. 1 hour, 2 hours, until morning, or until you resume.
* Notifications that arrive during a pause still queue and light afterwards if they're still there.
* The ring also goes dark on its own whenever a rear camera is open so it can't tint your photos.

**Other bits**

* Live preview of every pattern on a ring mock-up, plus a Test on LEDs button in each rule editor.
* Sort rule lists by first name, last name or app name.
* Guided onboarding that ticks each setting off as you flip it, with a notification that follows you into Settings for the pairing step.
* Debug log you can share if something goes wrong (contact names are left out).
* Back up and restore your rules to a file.
* Reset lights button for a stuck ring, and an automatic check on unlock.

**Requirements**

* Pixel 11 Pro, Pro XL or Pro Fold on Android 17.
* Developer options and Wireless debugging for setup. No root and no Shizuku needed: the app pairs once with the phone's own Wireless debugging to start a small helper that can reach the lights service, and reconnects by itself after a reboot.
* USB or Wireless debugging needs to stay on. Wi-Fi is needed whenever the helper has to start (setup, reboot, update) because Wireless debugging only runs on Wi-Fi.
* I'd recommend turning on "Disable adb authorization timeout" in Developer options, otherwise Android forgets the pairing after 7 days unused. The app suggests it but won't change it for you, and tells you if the pairing is lost.
* [Shizuku](https://shizuku.rikka.app/) still works too if you already use it.
* Turn off the stock "Calls from favourites" and "Gemini feedback" HiLight options so the two don't fight over the ring. Onboarding checks this.

It's open source under GPL-3.0: [github.com/mwilky/hilight-plus](https://github.com/mwilky/hilight-plus). Happy to answer questions or take suggestions, and if something doesn't work on your phone the debug log share in About is the quickest way to get it to me.
