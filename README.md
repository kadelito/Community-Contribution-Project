# MIDI Visualizer for Drummers
#### Video Demo: **URL HERE**
### Main Description:
This program was originally a final project for one of my classes. Its purpose was to solve some problem in a community (drummers struggling to learn songs, in my case), and my approach was to take MIDI files, which can represent any song, and, along with playing the song's audio, show visually how one would play the drums to the song.
### File Structure
This project was generated with IntelliJ Idea, so much of it is just "boilerplate" to make it work (mainly the `target` folder which I did not touch). The important files are in `src/main`.
<br>
The overall program relies mainly on the `javax.sound.midi` package, allowing for portability with only JDK packages used.
<br>
___
In the `resources/` folder are a few MIDI files I use for testing, along with the image of a drum kit, on top of which the main animation is played (and a few other drum images currently left unused).
<br>
In `java/org` holds three other packages, as well as the `Main.java` from which the program should be started.
___
The `midireading` package contains the `MIDIFormatter`, `TrackSettings`, and `MidiInfo` classes, the last of which only used for testing (I created this package first to figure out the structure of MIDI files). `MIDIFormatter` is a singleton object which, given a `Sequence` object (from the above-mentioned MIDI package) for reference, can convert various data taken from a `Sequence` object into data usable for my program. The purpose of a `TrackSettings` object is to record the current "state" of a MIDI track (such as tempo, the key, etc) updated using a MIDI event.
___
The `audio` package contains one class, `MusicHandler`. These objects are given the path to a MIDI file, and, with the `.loop()` method, plays whatever audio should be played at a given time.
___
The `visuals` package contains three classes. `Vec3` is a simple class that holds a 3D vector with double components and basic vector operations. In the future, I may use more sophisticated projection for a 3D animation, but as of now they act more as 2D vectors with a constant z component of 0. `VisMath` holds a few static methods that heavily use `Vec3`, mainly the `.bounce()` method which defines how the sticks should move between drums. Lastly is the `Visualizer` class, which also does not intend to have multiple instances. This class has its own set of methods for setting up the movement of the sticks and where/when the hit effects should occur. It uses these along with 
___
As of submitting, the `Main.java` file just runs the `Visualizer` main method. In the future, I may allow for more interaction, such as choosing from a song list, slowing/speeding the song by a given factor, etc.
