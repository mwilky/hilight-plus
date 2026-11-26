I've been working on an app for the Pixel 11 Pro's rear LEDs and it's now on Google Play. Out of the box the lights only do a few things: calls from favourites, Gemini's listening/thinking/replying lights, and on Android 17 QPR2 and later, messages from favourites. HiLight Plus takes them over so you can decide what they show.

The short version: a colour and pattern for each contact or app, a charging gauge, a low battery warning, a Do Not Disturb indicator, and rules for when the lights should stay dark. It can also redo Gemini's lights in whatever look you want.

And best of all, no root and no Shizuku needed!

[Google Play](https://play.google.com/store/apps/details?id=com.mwilky.hilight.plus) · [Source on GitHub](https://github.com/mwilky/hilight-plus) · [Screenshots](https://github.com/mwilky/hilight-plus#readme)

7-day trial with everything unlocked, then a one-off purchase. No subscription.

**Calls**

* Pick a colour and pattern per contact. Separate defaults for everyone else in your contacts and for unknown numbers.
* Starred contacts can share a favourites style if they don't have their own rule.
* Works for normal calls and app calls (WhatsApp, Teams, Meet and so on).
* Stays lit until you answer or reject. Optionally keeps going for a missed call until you clear the notification.
* An optional During a call look, basically an in-call indicator, so people around you can see you're on the phone. It stays lit at your ear even with pocket mode on.

**Notifications**

* Rules per sender and per app. App rules can pull the colour from the app icon.
* App rules can switch off individual notification channels, so promos or group chats stay dark while the rest of the app lights. Channels show up by name as the app posts on them.
* Text rules, for when a title or message contains (or is exactly) some text. Handy for delivery updates or verification codes, from any app or just one.
* App rules can light for progress, notifications or both. Start a timer in Clock and the lights go out one by one as it counts down, going amber at halfway and red near the end. Downloads and other progress bars light them one by one in the rule's colour instead, or the app icon's if it's progress only.
* 13 patterns in three groups: Simple, Motion and Gemini. Solid, Breathe, Pulse, Wave, Comet, Orbit, Beacon, Ripple, Sparkle, Rainbow, plus Gemini's own Listening, Thinking and Replying effects.
* Eight preset colours or anything from a colour wheel.
* Brightness from a dim glow to full, and speed from half to double the pattern's normal pace, per rule.
* Patterns run as looping effects on the lights' own controller, the same way stock Gemini does it, so they're smooth and the app isn't redrawing them many times a second.
* When several notifications are waiting you can show the newest only, take turns through them, or split the lights into a section per notification (up to four). The sections can breathe, stay solid, spotlight in turn or rotate round the lights, and each new notification can optionally play its own pattern once before the sections come back.
* Lights go off the moment the notification is cleared from the shade.

**Battery**

* Charging gauge, fill animation or gradient while plugged in.
* Red to green by charge level, or a fixed colour.
* Full charge indicator with a timeout.
* Low battery warning at whatever level you set.
* Battery sits under calls, Gemini, notifications and timers by default, and you can move it up (see Priority below).

**Gemini**

* Replaces the stock Gemini feedback with your own pattern and colour for listening, thinking and replying. Defaults match the stock look.
* Each state can be turned off or given its own conditions.
* This works by following Gemini's own log on the phone, so a Gemini update could break it until I catch up.

**Do Not Disturb**

* Lights up while Do Not Disturb is on, with its own colour, brightness and speed.
* Six calm patterns just for this: DND sign (the 3 and 9 o'clock LEDs), Crescent, Sleep, Snooze, Glance and Drift.
* Show it the whole time DND is on, or just for the first 10 seconds, 1 minute or 5 minutes.
* Optional sweep round the lights as DND turns on, and backwards as it turns off.

**Conditions**

* Face down only. The accelerometer only runs while something is waiting to light.
* I've seen the motion sensors stop sending readings to apps after hours of use, which only seems to happen on the Android 17 beta releases. The camera's affected too, and only a restart fixes it. Face-down rules can't light while that lasts, so the app spots it and tells you to restart with a notification and a card on Home. Both clear once readings come back.
* Stay dark during Do Not Disturb.
* Quiet hours, with per-rule overrides. Weekends can have their own times, or you can set each day separately.
* Pocket mode, on by default. No glowing through your jeans: the lights wait until the phone's out of your pocket. Face down on a table doesn't count, so face-down rules still work.
* Priority. The lights only show one thing at a time, so there's a list of Incoming calls, During a call, Gemini, Notifications, Timers & progress, Battery and DND that you drag into the order you want, and whichever is higher gets shown. That's the default order too. If something higher is hidden by its own conditions (say a face-down-only call while the phone's face up), the next one down shows instead.

**Pause**

* A Quick Settings tile pauses everything, or just calls, notifications, battery, Gemini or Do Not Disturb. 1 hour, 2 hours, until morning, or until you resume.
* Notifications that arrive during a pause still queue and light afterwards if they're still there.
* The lights also go dark on their own whenever a rear camera is open so they can't tint your photos.

**Other bits**

* Live preview of the lights for every pattern, plus a Test on LEDs button in each rule editor.
* Sort rule lists by first name, last name or app name.
* Guided onboarding that ticks each setting off as you flip it, with a notification that follows you into Settings for the pairing step.
* Debug log you can share if something goes wrong (contact names are left out).
* Back up and restore your rules to a file.
* Reset lights button for stuck lights, and an automatic check on unlock.

**Requirements**

* Pixel 11 Pro, Pro XL or Pro Fold on Android 17.
* Developer options and Wireless debugging for setup. No root and no Shizuku needed: the app pairs once with the phone's own Wireless debugging to start a small helper that can reach the lights service, and reconnects by itself after a reboot.
* USB or Wireless debugging needs to stay on. Wi-Fi is needed whenever the helper has to start (setup, reboot, update) because Wireless debugging only runs on Wi-Fi.
* On Android 17 QPR3 Beta 3 and later, Google stopped apps switching Wireless debugging on. The helper still can, so it switches it on as the phone shuts down, and the lights come back by themselves after a reboot as long as you're on a Wi-Fi network you've allowed it on. If you had it off, it goes back off once the lights are running. After a crash or a reboot elsewhere, the app tells you and starts the lights as soon as you turn it on. Leaving it on avoids that altogether.
* I'd recommend turning on "Disable adb authorization timeout" in Developer options, otherwise Android forgets the pairing after 7 days unused. The app suggests it but won't change it for you, and tells you if the pairing is lost.
* [Shizuku](https://shizuku.rikka.app/) works too, over wireless debugging or root, if you'd rather use that.
* Turn off the stock "Calls from favourites", "Gemini feedback" and (on QPR2 and later) "Messages from favourites" HiLight options so they don't fight over the lights. Onboarding checks this.

It's open source under GPL-3.0: [github.com/mwilky/hilight-plus](https://github.com/mwilky/hilight-plus). Happy to answer questions or take suggestions, and if something doesn't work on your phone the debug log share in About is the quickest way to get it to me.
