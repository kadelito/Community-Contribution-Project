package org.audio;

import org.midireading.MIDIFormatter;
import org.midireading.TrackSettings;

import javax.sound.midi.*;
import java.io.File;

import static javax.sound.midi.ShortMessage.*;

public class MusicHandler {

    private double slowFactor;

    private final Sequence sequence;
    private final MidiChannel[] channels;
    private final MIDIFormatter formatter;
    private final TrackSettings[] trackSettings;
    private final int[] trackProgress;

    private TrackSettings curSettings;
    private long curTick = 0;
    private long nextNearestTick = Long.MAX_VALUE;
    private long nextNanos = 0;

    int metronome = 0;
    long nextBeatNanos = 0;
    long deltaBeatNanos;
    private static long songStartNanos;

    public static void main(String[] args) throws Exception {
        MusicHandler music = new MusicHandler("src/main/resources/BasketCase.mid");
        while (true) {
            music.loop();
            Thread.sleep(20);
            music.endIfOver();
        }
    }

    public MusicHandler(String pathname) throws Exception {this(pathname, 1.0);}
    public MusicHandler(String pathname, double slowFactor) throws Exception {

        this.slowFactor = slowFactor;

        // Set up midi file
        sequence = MidiSystem.getSequence(new File(pathname));
        int numTracks = sequence.getTracks().length;
        formatter = MIDIFormatter.getInstance();
        formatter.setupFormatter(sequence);

        // Set up synthesizer
        Synthesizer synth = MidiSystem.getSynthesizer();
        synth.open();
        synth.loadAllInstruments(synth.getDefaultSoundbank());
        channels = synth.getChannels();

        // Initialize settings for each track
        trackSettings = new TrackSettings[numTracks];
        for (int i = 0; i < numTracks; i++)
            trackSettings[i] = new TrackSettings();
        trackProgress = new int[numTracks];

        songStartNanos = System.nanoTime();
    }

    public void loop() {

        // Don't do anything until the next nanosecond
        if (getSongNanos() > nextNanos) {

            // Iterate through tracks
            for (int i = 0; i < sequence.getTracks().length; i++) {
                Track track = sequence.getTracks()[i];

                curSettings = trackSettings[i];
                formatter.setTrackSettings(curSettings);

                // Play each event at curTick in parallel
                // Advance forward until next event is on the next tick OR the end of track
                for (int j = trackProgress[i]; j < track.size(); j++) {
                    MidiEvent event = track.get(j);

                    if (event.getTick() > curTick) {
                        nextNearestTick = Math.min(nextNearestTick, event.getTick());
                        trackProgress[i] = j;
                        break;
                    }

                    // Otherwise, it's the current tick
                    processMidiEvent(event);
                }
            }

            // After all tracks done, update timings
            curTick = nextNearestTick;
            nextNanos = (long) (slowFactor * trackSettings[0].tickToNanos(nextNearestTick));
            deltaBeatNanos = (long) (slowFactor * trackSettings[0].tickToNanos(sequence.getResolution()));

            // Reset next tick to be 'infinitely' far away
            // (will be overwritten by first Math.min call)
            nextNearestTick = Long.MAX_VALUE;
        }

        // Call metronome
        if (getSongNanos() > nextBeatNanos) {
            click();
        }
    }

    public void click() {
        channels[9].noteOff(76);
        channels[9].noteOff(77);
        channels[9].noteOn(metronome % 4 == 0 ? 76:77, 127);

        nextBeatNanos += deltaBeatNanos;
        metronome++;
    }

    public void endIfOver() {
        long endTick = sequence.getTickLength();
        long endNanos = trackSettings[0].tickToNanos(endTick);

        if (getSongNanos() > endNanos + 2L * NANOS_PER_SEC)
            System.exit(0);
    }

    // Processes a MidiEvent
    public void processMidiEvent(MidiEvent event) {

        // Update track settings & get channel
        MidiMessage message = event.getMessage();
        curSettings.update(message);
        MidiChannel curChannel = channels[curSettings.getChannel()];

        // If it's a ShortMessage, do that stuff
        if (message instanceof ShortMessage shortMessage) {
            double velocityMult = shortMessage.getChannel() == 9 ?
                    0.85 : 0.7;
            int data1 = shortMessage.getData1();
            int data2 = shortMessage.getData2();
            switch (shortMessage.getCommand()) {
                case NOTE_ON:
                    curChannel.noteOn(data1, (int)(data2 * velocityMult));
                    break;

                case NOTE_OFF:
                    curChannel.noteOff(data1, data2);
                    break;

                case POLY_PRESSURE:
                    curChannel.setPolyPressure(data1, data2);
                    break;

                case CHANNEL_PRESSURE:
                    curChannel.setChannelPressure(data1);

                case CONTROL_CHANGE:
                    curChannel.controlChange(data1, data2);
                    break;

                case PROGRAM_CHANGE:
                    curChannel.programChange(data1);
                    break;

                case PITCH_BEND:
                    curChannel.setPitchBend(data1 + (data2 << 7));
            }
        }
        
    }

    // Returns the current nanoseconds relative to the start of the song
    public long getSongNanos() {
        return System.nanoTime() - songStartNanos;
    }

    private static final int NANOS_PER_SEC = 1_000_000_000;
}