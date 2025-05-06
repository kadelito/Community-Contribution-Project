package org.midireading;

import javax.sound.midi.*;
import java.util.HashMap;

import static javax.sound.midi.ShortMessage.*;
import static org.midireading.TrackSettings.metaTypes.*;

public class MIDIFormatter {
    
    private static MIDIFormatter instance;
    private TrackSettings settings;
    private double ticksPerQuarterNote;

    // Returns a formatted string of a given MidiMessage's information
    // Assumed that settings was updated before this is called
    public String formatMidiMessage(MidiMessage message) {

        StringBuilder sb = new StringBuilder();

        if (message instanceof ShortMessage shortMsg) {
            sb.append(String.format("Ch%d\t", shortMsg.getChannel()));

            // If message is note on or off, print it all fancy-like
            if (shortMsg.getCommand() == NOTE_ON || shortMsg.getCommand() == NOTE_OFF) {
                String note;
                if (settings.getChannel() == 9) // or 10 if 1-indexed
                    note = MIDIFormatter.getPercussion(shortMsg.getData1(), false);
                else
                    note = MIDIFormatter.getNote(shortMsg.getData1());

                sb.append(String.format("%s %s\t|",
                        note,
                        shortMsg.getCommand() == NOTE_ON ? "On " : "Off"
                        ));
                if (shortMsg.getCommand() == NOTE_ON)
                    // velocity
                    sb.append(" force: ").append(shortMsg.getData2());
            } else {
                sb.append(shortMessageNames.get(shortMsg.getCommand())).append(": ");
                if (shortMsg.getCommand() == PROGRAM_CHANGE)
                    sb.append(getInstrument(shortMsg.getData1()));
                else
                    sb.append(String.format("(%02X, %02X)", shortMsg.getData1(), shortMsg.getData2()));
            }

        // otherwise, it's a system-exclusive or meta-event
        } else {
            byte[] bytes = message.getMessage();
            // If it's a meta event, some setting is probably modified
            if (message instanceof MetaMessage meta) {
                sb.append(showSetting(meta));
            // For sysex events, show entire message bc idk what to do with it :3
            } else if (message instanceof SysexMessage) {
                sb.append("Sysex message: ");
                for (byte b: bytes)
                    sb.append(String.format("%02X ", b));
            } else
                throw new RuntimeException("MidiMessage is not a ShortMessage, MetaMessage, or SysexMessage");
        }
        return sb.toString();
    }

    // Returns the instrument corresponding to a program number
    public static String getInstrument(int programNumber) {
        return INSTRUMENT_MAP[programNumber];
    }

    // Returns a percussive instrument given the key of a ShortMessage (MIDI control event)
    public static String getPercussion(int key, boolean shorten) {
        return shorten ? PERCUSSION_MAP[key - 35] : String.format("%-18s", PERCUSSION_MAP[key - 35]);
    }

    // Returns a note given the key of a ShortMessage (MIDI control event)
    public static String getNote(int key) {
        int octave = (key / 12) - 1;
        int note = key % 12;
        return NOTE_NAMES_SHARP[note] + octave;
    }

    // Displays certain data from a MetaMessage, uses a TrackSettings object if possible
    public String showSetting(MetaMessage meta) {
        String str = metaEventNames.get(meta.getType()) + ": ";
        if ((meta.getType() & 0x0F) == meta.getType())
            return str + textEvent(meta.getMessage());

        str += switch (meta.getType()) {
            case SEQUENCE_NUMBER -> bytesToNum(meta.getMessage(), 3, 2);
            case TEXT_EVENT -> textEvent(meta.getMessage());
            case TRACK_NAME -> settings.getName();
            case INSTRUMENT_NAME -> settings.getInstrument();
            case CHANNEL_PREFIX -> settings.getChannel();
            case SET_TEMPO -> settings.getBpm() + " BPM";
            case SMPTE_OFFSET -> settings.getOffset().toString();
            case TIME_SIGNATURE -> settings.getTimeSigNum() + "/" + settings.getTimeSigType();
            case KEY_SIGNATURE -> settings.getKey();
            case SEQUENCER_SPECIFIC, END_OF_TRACK -> "";
            default -> "?";
        };

        if (meta.getType() == SEQUENCER_SPECIFIC) {
            StringBuilder sb = new StringBuilder();
            byte[] bytes = meta.getMessage();
            for (int i = 3; i < bytes[2]; i++)
                sb.append(String.format("%02X ", bytes[i]));
            str += sb.toString();
        }
        return str;
    }

    // Returns a string from the bytes of a MIDI text event
    public static String textEvent(byte[] bytes) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < bytes[2]; i++)
            str.append((char)bytes[i + 3]);
        return str.toString();
    }

    // Converts a tempo (in microseconds per quarter-note) into quarter-notes(beats) per minute
    // Primarily for display purposes
    public static int tempoToBPM(int microseconds) {
        double real = MINUTE_PER_MICROSECOND / microseconds;
        int bpm = (int) Math.round(real);
        if (Math.abs(real - bpm) > ERROR_THRESHOLD)
            throw new RuntimeException("Non-integer BPM detected! (" + real + ")");
        return bpm;
    }

    // Converts a tempo (in microseconds per quarter-note) into nanoseconds per midi tick
    public long tempoToNanosPerTick(int microsPerQuarter) {
        double real = microsPerQuarter / ticksPerQuarterNote * 1000L;
        return (long) real;
    }

    // Converts an integer into binary-coded decimal
    // im lazy :3
    public static int bcd(int num) {
        return Integer.parseInt(String.format("%x", num));
    }

    // Converts an array of bytes, from index -> index + numBytes, into an integer
    public static int bytesToNum(byte[] bytes, int index, int numBytes) {
        if (numBytes > 4 || numBytes < 1)
            throw new UnsupportedOperationException("Invalid number bytes for a 32-bit integer!");
        int sum = 0;
        for (int i = 0; i < numBytes; i++)
            sum |= (bytes[index + i] & 0xFF) << ((numBytes - i - 1) * 8);
        return sum;
    }

    public static MIDIFormatter getInstance() {
        if (instance == null)
            instance = new MIDIFormatter();
        return instance;
    }

    public void setupFormatter(Sequence sequence) {
        initializeMaps();
        if (sequence.getDivisionType() == Sequence.PPQ)
            ticksPerQuarterNote = sequence.getResolution();
        else
            throw new RuntimeException("SMPTE timing not supported!");
    }

    public void setTrackSettings(TrackSettings settings) {
        this.settings = settings;
    }

    public void initializeMaps() {
        metaEventNames.put(SEQUENCE_NUMBER,     "Sequence Number");
        metaEventNames.put(TEXT_EVENT,          "Text event");
        metaEventNames.put(COPYRIGHT_NOTICE,    "Copyright notice");
        metaEventNames.put(TRACK_NAME,          "Track name");
        metaEventNames.put(INSTRUMENT_NAME,     "Instrument name");
        metaEventNames.put(LYRIC,               "Lyric");
        metaEventNames.put(MARKER,              "Marker");
        metaEventNames.put(CUE_POINT,           "Cue point");
        metaEventNames.put(PROGRAM_NAME,        "Program name");
        metaEventNames.put(DEVICE_NAME,         "Device name");
        metaEventNames.put(CHANNEL_PREFIX,      "MIDI channel prefix");
        metaEventNames.put(END_OF_TRACK,        "End of track");
        metaEventNames.put(SET_TEMPO,           "Set tempo");
        metaEventNames.put(SMPTE_OFFSET,        "SMPTE offset");
        metaEventNames.put(TIME_SIGNATURE,      "Time signature");
        metaEventNames.put(KEY_SIGNATURE,       "Key signature");
        metaEventNames.put(SEQUENCER_SPECIFIC,  "Sequencer-specific event");

        shortMessageNames.put(ACTIVE_SENSING,	    "Active Sensing");
        shortMessageNames.put(CHANNEL_PRESSURE,     "Channel Pressure");
        shortMessageNames.put(CONTINUE,	            "Continue");
        shortMessageNames.put(CONTROL_CHANGE,	    "Control Change");
        shortMessageNames.put(END_OF_EXCLUSIVE,	    "End Of Exclusive");
        shortMessageNames.put(MIDI_TIME_CODE,	    "Midi Time Code");
        shortMessageNames.put(NOTE_OFF,	            "Note Off");
        shortMessageNames.put(NOTE_ON,	            "Note On");
        shortMessageNames.put(PITCH_BEND,	        "Pitch Bend");
        shortMessageNames.put(POLY_PRESSURE,	    "Poly Pressure");
        shortMessageNames.put(PROGRAM_CHANGE,	    "Program Change");
        shortMessageNames.put(SONG_POSITION_POINTER,"Song Position Pointer");
        shortMessageNames.put(SONG_SELECT,	        "Song Select");
        shortMessageNames.put(START,	            "Start");
        shortMessageNames.put(STOP,	                "Stop");
        shortMessageNames.put(SYSTEM_RESET,	        "System Reset");
        shortMessageNames.put(TIMING_CLOCK,	        "Timing Clock");
        shortMessageNames.put(TUNE_REQUEST,	        "Tune Request");
    }

    private static final double ERROR_THRESHOLD = 1e-4;

    private static final double MINUTE_PER_MICROSECOND = 6e7;

    private static final HashMap<Integer, String> metaEventNames = new HashMap<>(15);
    private static final HashMap<Integer, String> shortMessageNames = new HashMap<>(15);

    private static final String[] NOTE_NAMES_SHARP = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    private static final String[] NOTE_NAMES_FLAT = {"C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"};

    private static final String[] PERCUSSION_MAP = new String[] {
            "Acoustic Bass Drum",
            "Bass Drum 1",
            "Side Stick",
            "Acoustic Snare",
            "Hand Clap",
            "Electric Snare",
            "Low Floor Tom",
            "Closed Hi Hat",
            "High Floor Tom",
            "Pedal Hi-Hat",
            "Low Tom",
            "Open Hi-Hat",
            "Low-Mid Tom",
            "Hi-Mid Tom",
            "Crash Cymbal 1",
            "High Tom",
            "Ride Cymbal 1",
            "Chinese Cymbal",
            "Ride Bell",
            "Tambourine",
            "Splash Cymbal",
            "Cowbell",
            "Crash Cymbal 2",
            "Vibraslap",
            "Ride Cymbal 2",
            "Hi Bongo",
            "Low Bongo",
            "Mute Hi Conga",
            "Open Hi Conga",
            "Low Conga",
            "High Timbale",
            "Low Timbale",
            "High Agogo",
            "Low Agogo",
            "Cabasa",
            "Maracas",
            "Short Whistle",
            "Long Whistle",
            "Short Guiro",
            "Long Guiro",
            "Claves",
            "Hi Wood Block",
            "Low Wood Block",
            "Mute Cuica",
            "Open Cuica",
            "Mute Triangle",
            "Open Triangle"
    };
    private static final String[] INSTRUMENT_MAP = new String[] {
            null,
            "Acoustic Grand Piano",
            "Bright Acoustic Piano",
            "Electric Grand Piano",
            "Honky-tonk Piano",
            "Electric Piano 1",
            "Electric Piano 2",
            "Harpsichord",
            "Clavi",
            "Celesta",
            "Glockenspiel",
            "Music Box",
            "Vibraphone",
            "Marimba",
            "Xylophone",
            "Tubular Bells",
            "Dulcimer",
            "Drawbar Organ",
            "Percussive Organ",
            "Rock Organ",
            "Church Organ",
            "Reed Organ",
            "Accordion",
            "Harmonica",
            "Tango Accordion",
            "Acoustic Guitar (nylon",
            "Acoustic Guitar (steel)",
            "Electric Guitar (jazz)",
            "Electric Guitar (clean)",
            "Electric Guitar (muted",
            "Overdriven Guitar",
            "Distortion Guitar",
            "Guitar harmonics",
            "Acoustic Bass",
            "Electric Bass (finger)",
            "Electric Bass (pick)",
            "Fretless Bass",
            "Slap Bass 1",
            "Slap Bass 2",
            "Synth Bass 1",
            "Synth Bass 2",
            "Violin",
            "Viola",
            "Cello",
            "Contrabass",
            "Tremolo Strings",
            "Pizzicato Strings",
            "Orchestral Harp",
            "Timpani",
            "String Ensemble 1",
            "String Ensemble 2",
            "SynthStrings 1",
            "SynthStrings 2",
            "Choir Aahs",
            "Voice Oohs",
            "Synth Voice",
            "Orchestra Hit",
            "Trumpet",
            "Trombone",
            "Tuba",
            "Muted Trumpet",
            "French Horn",
            "Brass Section",
            "SynthBrass 1",
            "SynthBrass 2",
            "Soprano Sax",
            "Alto Sax",
            "Tenor Sax",
            "Baritone Sax",
            "Oboe",
            "English Horn",
            "Bassoon",
            "Clarinet",
            "Piccolo",
            "Flute",
            "Recorder",
            "Pan Flute",
            "Blown Bottle",
            "Shakuhachi",
            "Whistle",
            "Ocarina",
            "Lead 1 (square)",
            "Lead 2 (sawtooth)",
            "Lead 3 (calliope)",
            "Lead 4 (chiff)",
            "Lead 5 (charang)",
            "Lead 6 (voice)",
            "Lead 7 (fifths)",
            "Lead 8 (bass + lead)",
            "Pad 1 (new age)",
            "Pad 2 (warm)",
            "Pad 3 (polysynth)",
            "Pad 4 (choir)",
            "Pad 5 (bowed)",
            "Pad 6 (metallic)",
            "Pad 7 (halo)",
            "Pad 8 (sweep)",
            "FX 1 (rain)",
            "FX 2 (soundtrack)",
            "FX 3 (crystal)",
            "FX 4 (atmosphere)",
            "FX 5 (brightness)",
            "FX 6 (goblins)",
            "FX 7 (echoes)",
            "FX 8 (sci-fi)",
            "Sitar",
            "Banjo",
            "Shamisen",
            "Koto",
            "Kalimba",
            "Bag pipe",
            "Fiddle",
            "Shanai",
            "Tinkle Bell",
            "Agogo",
            "Steel Drums",
            "Woodblock",
            "Taiko Drum",
            "Melodic Tom",
            "Synth Drum",
            "Reverse Cymbal",
            "Guitar Fret Noise",
            "Breath Noise",
            "Seashore",
            "Bird Tweet",
            "Telephone Ring",
            "Helicopter",
            "Applause",
            "Gunshot"
    };
}