# Mordhau Mapping Automation

## Tech Stack

This tool is written in Java and uses Gradle for package management and JavaFX for the user interface.

## Goals

With one click of a button, this tool is able to automatically cook your map from the command line and copy it into your Mordhau directory. It is also capable of copying custom cooked files given a certain folder. No longer do you have to worry about managing many explorer windows and manually copying files!

## Defaults

You can create a Defaults.txt in the working directory of the program and set the following presets:

BinaryDirectory
MordhauInstallDirectory
UnrealProjectDirectory
UnrealProjectCustomCopyDirectory

Using the .ini file format. An example of an entry would be:

`UnrealProjectDirectory=C:\Users\EpicGamer\Documents\Unreal Projects\MordhauMap`
