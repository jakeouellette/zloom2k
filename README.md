# zloom2k

This is a Kotlin fork of zloom2, refactoring its codebase in several ways with the goal of ultimately improving the UX. Some changes:

* Kotlinified, making nullability clearer
* Integrated with Gradle
* (WIP) Refactoring State. Reducing Global mutables
* (WIP) Improving the UX. Exposing tools and making editing more intuitive.

This is a fork written by Jake Ouellette.

## Original readme

ZZT and Super ZZT world editor

This is a Weave 3 hack of the MIT-licensed release of zedit2 - past versions can be obtained from https://zedit2.skyend.net/ - hacked versions for Weave 3 can be found at https://meangirls.itch.io/weave-3

The code is licensed under MIT. The font (Px437_IBM_EGA8.ttf) is licensed under CC-BY-SA and is (c) VileR.

This is an [IntelliJ IDEA](https://www.jetbrains.com/idea/) project and requires a JDK version of at least 11 (the project is configured to use [Amazon Corretto 11](https://docs.aws.amazon.com/corretto/latest/corretto-11-ug/downloads-list.html) but any JDK 11 should do.)

The code quality is not great - it was originally designed as a fairly simple editor, but then the complexity grew as I added more features and I never got around to giving it a good refactoring. The worst offender is probably the handling of keybinds, where new keyboard shortcuts need to be added in 3 places (to the keymappings array in Settings.java:keystrokeConfig(), to GlobalEditor.java:initProperties() and to WorldEditor.java:addKeybinds()).

Unfortunately I do not have time to maintain this software any more.
