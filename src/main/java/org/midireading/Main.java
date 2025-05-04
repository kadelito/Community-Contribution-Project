package org.midireading;

import javax.sound.midi.*;
import java.io.File;

import static org.midireading.TrackSettings.metaTypes.*;

public class Main {

    public static void main(String[] args) throws Exception {

        Sequence sequence = MidiSystem.getSequence(new File("src/main/resources/1979.mid"));
        MIDIFormatter formatter = MIDIFormatter.getInstance();
        formatter.setupFormatter(sequence);

        System.out.println("resolution: " + sequence.getResolution());
        System.out.println("div type: " + sequence.getDivisionType());
        if (sequence.getDivisionType() == Sequence.PPQ)
            System.out.println("PPQ");
        else
            throw new RuntimeException("Non-PPQ timing is not supported!");

        int tickDigits = 1 + (int)Math.log10(sequence.getTickLength());

        /*
        TODO  o----------------------------------------o
        TODO  | - Play music along w/ vis              |
        TODO  | - Make sure music & vis are in sync    |
        TODO  o----------------------------------------o
        */

        TrackSettings settings = new TrackSettings();
        formatter.setTrackSettings(settings);

        // Iterate through tracks
        for (int i = 0; i < sequence.getTracks().length; i++) {
            Track track = sequence.getTracks()[i];
            System.out.println("=====================================================");
            System.out.println("Track " + i + ": size = " + track.size());
            System.out.printf("Track %d ( events: %d,\tlength: %d ticks )\n", i, track.size(), track.ticks());

            long prevTick = 0;

            System.out.printf("t = %" + tickDigits + "d |\tTrack start\n", 0);

            // Iterate through each event in track
            for (int j = 0; j < track.size(); j++) {
                MidiEvent event = track.get(j);
                MidiMessage message = event.getMessage();

                settings.update(message);
                long deltaTick = event.getTick() - prevTick;
//                if (deltaTick > 0)
//                    System.out.printf("t = %" + tickDigits + "d |\t", event.getTick());
//                else
//                    System.out.print(centerString(5 + tickDigits, "...") + "|\t");
//                System.out.println(formatter.formatMidiMessage(message));


//                settings.sleep(deltaTick);`

                prevTick = event.getTick();
            }
            System.out.println("-------------------------------");
            System.out.println(settings.getChannel());
            System.out.println("=====================================================\n");
        }
    }

    public static String centerString (int width, String s) {
        return String.format("%-" + width  + "s", String.format("%" + (s.length() + (width - s.length()) / 2) + "s", s));
    }
}