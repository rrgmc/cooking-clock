# Cooking Clock application

Create an Android application for managing cooking clocks. The clocks will always be countdown clocks.

It must support multiple clocks at the same time. Each clock will be in an own line. It will start with the clock name, then the countdown time, then the settings (pause, reset, delete). Clicking in the time allows to change the time (+1, +5, -1, -5). Clicking the increase/decrease should change the current value. Each clock might also allow selecting a custom background color, which could be "unset".

Clock names must be a maintained list. When naming a clock, a popup should open allowing a name to be typed, or picking from a list of some previous names. Typing a new name will add it to that list. In this same screen it must be possible to delete names from that list.

The application must handle background jobs so the alerts never fail to happen. The android notification should always be available when there are clocks running, they should show all active clocks in a layout that fits all of them (if there are more blocks, more compact).

There must be a settings screen, where a number of alarm profiles can be created. Each alarm profile will have an alarm sound, and wether to vibrate or not. Each clock must allow setting a custom alarm profile. A default profile must exist, and will be used by default.

There must be a "clear all" option in the main screen, with confirmation. All deletes should have confirmation.
There must be a "keep screen on" button in the main app to keep the screen on.

A home screen widget must be available showing all the active clocks and their countdown times.

The application theme will be "light".
