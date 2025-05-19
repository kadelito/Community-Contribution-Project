package org.midireading;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import java.time.LocalTime;

public class TrackSettings {

    public static class metaTypes {
        public static final int SEQUENCE_NUMBER = 0x00;

        // Text events: FF 01 -> 0F
        public static final int TEXT_EVENT = 0x01;
        public static final int COPYRIGHT_NOTICE = 0x02;
        public static final int TRACK_NAME = 0x03;
        public static final int INSTRUMENT_NAME = 0x04;
        public static final int LYRIC = 0x05;
        public static final int MARKER = 0x06;
        public static final int CUE_POINT = 0x07;
        public static final int PROGRAM_NAME = 0x08;
        public static final int DEVICE_NAME = 0x09;

        public static final int CHANNEL_PREFIX = 0x20;
        public static final int END_OF_TRACK = 0x2F;
        public static final int SET_TEMPO = 0x51;
        public static final int SMPTE_OFFSET = 0x54;
        public static final int TIME_SIGNATURE = 0x58;
        public static final int KEY_SIGNATURE = 0x59;
        public static final int SEQUENCER_SPECIFIC = 0x7F;
    }

    /*              META EVENT DATA            */
    private String name;                // Name of current track
    private String instrument;          // Name of current instrument
    private int channel;                // Messages between updates are in this channel
    private long nanosPerTick = 1;      // Used for timings
    private int bpm;                    // Mostly for display
    private LocalTime offset = LocalTime.now();     // idk how to use this one
    private int timeSigNum;             // Number of Nth-notes in a bar (6 in 6/8)
    private int timeSigType;            // Type of notes in a bar (8 in 6/8)
    private int keyNote;                // Ranges from 7 flats to 7 sharps around C major
    private boolean majorKey;           // false -> minor key

    // General update method for a MidiMessage
    public void update(MidiMessage message) {
        if (message instanceof MetaMessage mt)
            updateMetaData(mt);
        else if (message instanceof ShortMessage sm)
            channel = sm.getChannel();
    }

    // Updates data given a MetaMessage
    public void updateMetaData(MetaMessage mess) {
        byte[] bytes = mess.getMessage();
        int type = bytes[1];
        switch (type) {
            case metaTypes.TRACK_NAME:
                name = MIDIFormatter.textEvent(bytes);
                break;

            case metaTypes.INSTRUMENT_NAME:
                instrument = MIDIFormatter.textEvent(bytes);
                break;

            case metaTypes.CHANNEL_PREFIX:
                channel = bytes[3];
                break;

            case metaTypes.SET_TEMPO:
                int tempo = MIDIFormatter.bytesToNum(bytes, 3, 3);
                nanosPerTick = MIDIFormatter.getInstance().tempoToNanosPerTick(tempo);
                bpm = MIDIFormatter.tempoToBPM(tempo);
                break;

            case metaTypes.SMPTE_OFFSET:
                // i have no idea what to do with the fractional frames into nanoseconds
                // doesnt matter if PPQ (:
                offset = LocalTime.of(
                        MIDIFormatter.bcd(bytes[3]),
                        MIDIFormatter.bcd(bytes[4]),
                        MIDIFormatter.bcd(bytes[5])
                );
                break;

            case metaTypes.TIME_SIGNATURE:
                timeSigNum = bytes[3];
                timeSigType = 1 << bytes[4];
                break;

            case metaTypes.KEY_SIGNATURE:
                keyNote = bytes[3];
                majorKey = bytes[4] == 0;
                break;
        }
    }

    // Sleeps for a given amount of midi ticks based on timing settings
    // Mainly for development purposes, final version should not use this
    public void sleep(long ticks) throws InterruptedException {
        long nanos = nanosPerTick * ticks;
        Thread.sleep(nanos / 1_000_000L, (int)(nanos % 1_000_000L));
    }

    // Converts ticks into nanoseconds
    public long tickToNanos(long tick) {
        return tick * nanosPerTick;
    }

    /*=============== Accessor Methods ===============*/

    public String getName() {
        return name;
    }

    public String getInstrument() {
        return instrument;
    }

    public int getChannel() {
        return channel;
    }

    public int getBpm() {
        return bpm;
    }

    public LocalTime getOffset() {
        return offset;
    }

    public int getTimeSigNum() {
        return timeSigNum;
    }

    public int getTimeSigType() {
        return timeSigType;
    }

    public String getKey() {
        return "Key of " + (
                keyNote == 0 ? "C" : Math.abs(keyNote) + (
                        keyNote > 0 ? " sharps" : " flats")
        ) + (majorKey ? " (Major)" : " (Minor)");
    }
}
