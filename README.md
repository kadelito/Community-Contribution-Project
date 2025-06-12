# MIDI Visualizer for Drummers
#### Video Demo: <strong>URL HERE</strong>
### Main Description:
This program was originally a final project for one of my classes. Its purpose was to solve some problem in a community (*drummers struggling to learn songs, in my case*), and my approach was to take MIDI files, which can represent any song, and, along with playing the song's audio, show visually how one would play the drums to the song.
### File Structure
This project was generated with IntelliJ Idea, so much of it is just "boilerplate" to make it work (*mainly the `target` folder which I did not touch*). The important files are in `src/main`.
<br>
The overall program relies mainly on the `javax.sound.midi` package, allowing for portability with only JDK packages used.
<br>
___
In the `resources/` folder are a few MIDI files I use for testing, along with the image of a drum kit, on top of which the main animation is played (*and a few other drum images currently left unused*).
<br>
In `java/org` holds three other packages, as well as the `Main.java` from which the program should be started. The `midireading` package contains the `MIDIFormatter`, `TrackSettings`, and `MidiInfo` classes, the last of which only used for testing. `MIDIFormatter` is a singleton object which, given a `Sequence` object (*from the above-mentioned MIDI package*) for reference, can convert various data taken from a `Sequence` object into data usable for 
<br>
