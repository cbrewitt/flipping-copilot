# AGENTS
* This is the repo for a Runelite plugin called Flipping Copilot that assists users flipping on the grand exchange in the game Old School Runescape.
* The plugin is written in Java (version 11), using the Swing framework, as well as the custom framework and utilities provided by the main Runelite Java library / jar for building plugins.
* The build process uses gradle.
* There is a test (`src/test/java/com/flippingcopilot/FlippingCopilotPluginTest.java`) which when run launches the Runelite client with the plugin installed for the current state of the code.
* The plugin communicates with a remote server, that code of which lives in a different repo. The remote server handles persisting users flips and the main pricing/flipping algorithm.