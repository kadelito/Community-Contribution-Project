package org.audio;

import org.midireading.MIDIFormatter;
import org.midireading.TrackSettings;

import javax.sound.midi.*;
import java.io.File;

import static javax.sound.midi.ShortMessage.*;

public class MusicHandler {
    
    private static long songStartNanos;

     /**
      * <pre>
      * pseudocode for music (i think)
      * song start = current time
      * while song not over {
      *     for each track {
      *         for each event {
      *             if event.tick > current tick
      *                 outta here
      *             else
      *                 play event or modify settings or whatever
      *         }
      *     }
      *     wait until next closest event (in ticks)
      * }
      * </pre
      */
    public static void main(String[] args) throws Exception {
        // Set up synthesizer
        Synthesizer synth = MidiSystem.getSynthesizer();
        synth.open();
        synth.loadAllInstruments(synth.getDefaultSoundbank());
        MidiChannel[] channels = synth.getChannels();

        // Set up midi file
        Sequence sequence = MidiSystem.getSequence(new File("src/main/resources/Numb.mid"));
        MIDIFormatter formatter = MIDIFormatter.getInstance();
        formatter.setupFormatter(sequence);

        // Initialize settings for each track
        TrackSettings[] trackSettings = new TrackSettings[sequence.getTracks().length];
        for (int i = 0; i < trackSettings.length; i++)
            trackSettings[i] = new TrackSettings();

        long curTick = 0;
        long nextTick = 0;

        songStartNanos = System.nanoTime();

        while (curTick < sequence.getTickLength()) {

            // Wait until next tick
            if (trackSettings[0].nanosToTick(getSongNanos()) > nextTick) {
                nextTick = Long.MAX_VALUE;
                
                // Iterate through tracks
                for (int i = 0; i < sequence.getTracks().length; i++) {
                    Track track = sequence.getTracks()[i];

                    TrackSettings curSettings = trackSettings[i];
                    formatter.setTrackSettings(curSettings);

                    // Play each event at curTick in parallel
                    for (int j = 0; j < track.size(); j++) {
                        MidiEvent event = track.get(j);

                        // TODO: replace 'wasted loops' with an array of indices(progress) for each track
                        if (event.getTick() < curTick) continue;

                            // Get the next nearest tick
                        else if (event.getTick() > curTick) {
                            nextTick = Math.min(nextTick, event.getTick());
                            break;
                        }
                        // Otherwise, it's the current tick
                        
                        processMidiEvent(event, curSettings, channels);
                    }
                }
            }
            
            // After all tracks done, update tick
            curTick = nextTick;
        }
        
        // Prevent final note(s) from cutting off
        Thread.sleep(1000);
    }
    
    public static void processMidiEvent(MidiEvent event, TrackSettings curSettings, MidiChannel[] channels) {

        MidiMessage message = event.getMessage();
        curSettings.update(message);
        MidiChannel curChannel = channels[curSettings.getChannel()];
        curChannel.programChange(curSettings.getProgramNumber());
        curChannel.setPitchBend(curSettings.getPitchBend());

        if (message instanceof ShortMessage sMessage) {
            switch (sMessage.getCommand()) {
                case NOTE_ON:
                    curChannel.noteOn(sMessage.getData1(), sMessage.getData2());
                    break;

                case NOTE_OFF:
                    curChannel.noteOff(sMessage.getData1(), sMessage.getData2());
                    break;
            }
        }
        
    }
    
    public static long getSongNanos() {
        return System.nanoTime() - songStartNanos;
    }
}