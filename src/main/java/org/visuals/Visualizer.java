package org.visuals;

import org.midireading.MIDIFormatter;
import org.midireading.TrackSettings;

import javax.sound.midi.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public class Visualizer extends JPanel {

    private final int WIDTH = 800;
    private final int HEIGHT = 600;
    private final int CENTER_X = WIDTH / 2;
    private final int CENTER_Y = HEIGHT / 2;

    private BufferedImage drumKitImage;
    private final int DRUM_X;
    private final int DRUM_Y;

    private final int REFRESH_RATE = 16;

    private Graphics2D g;

    /*====================== Actual visualizer stuff ======================*/

    long programStartNanos;

    private Map<DrumPart, Vec3> drumPoints;

    // Constants for mapping to drumPoints
    private enum DrumPart {
        SNARE, HI_TOM, MID_TOM, FLOOR_TOM,
        HI_HAT, CRASH, RIDE, CRASH_2,
        KICK, HI_HAT_PEDAL,
        MISC
    }

    // Maps the general MIDI Percussion Map to drumPoint indices (offset by 35)
    DrumPart[] drumKeyMap = new DrumPart[] {
            DrumPart.KICK,			// Acoustic Bass Drum
            DrumPart.KICK,			// Bass Drum 1
            DrumPart.MISC,			// Side Stick
            DrumPart.SNARE,			// Acoustic Snare
            DrumPart.SNARE,			// Hand Clap
            DrumPart.SNARE,			// Electric Snare
            DrumPart.FLOOR_TOM,		// Low Floor Tom
            DrumPart.HI_HAT,			// Closed Hi Hat
            DrumPart.FLOOR_TOM,		// High Floor Tom
            DrumPart.HI_HAT_PEDAL,	// Pedal Hi-Hat
            DrumPart.FLOOR_TOM,		// Low Tom
            DrumPart.HI_HAT,			// Open Hi-Hat
            DrumPart.MID_TOM,		// Low-Mid Tom
            DrumPart.MID_TOM,		// Hi Mid Tom
            DrumPart.CRASH,			// Crash Cymbal 1
            DrumPart.HI_TOM,			// High Tom
            DrumPart.RIDE,			// Ride Cymbal 1
            DrumPart.CRASH,			// Chinese Cymbal
            DrumPart.RIDE,			// Ride Bell
            DrumPart.HI_HAT,			// Tambourine
            DrumPart.CRASH_2,		// Splash Cymbal
            DrumPart.MISC,			// Cowbell
            DrumPart.CRASH_2,		// Crash Cymbal 2
            DrumPart.MISC,			// Vibraslap
            DrumPart.RIDE,			// Ride Cymbal 2
            DrumPart.HI_TOM,			// Hi Bongo
            DrumPart.MID_TOM,		// Low Bongo
            DrumPart.HI_TOM,			// Mute Hi Conga
            DrumPart.HI_TOM,			// Open Hi Conga
            DrumPart.MID_TOM,		// Low Conga
            DrumPart.HI_TOM,			// High Timbale
            DrumPart.MID_TOM,		// Low Timbale
            DrumPart.MISC,			// High Agogo
            DrumPart.MISC,			// Low Agogo
            DrumPart.MISC,			// Cabasa
            DrumPart.MISC,			// Maracas
            DrumPart.MISC,			// Short Whistle
            DrumPart.MISC,			// Long Whistle
            DrumPart.MISC,			// Short Guiro
            DrumPart.MISC,			// Long Guiro
            DrumPart.MISC,			// Claves
            DrumPart.MISC,			// Hi Wood Block
            DrumPart.MISC,			// Low Wood Block
            DrumPart.HI_TOM,			// Mute Cuica
            DrumPart.HI_TOM,			// Open Cuica
            DrumPart.MISC,			// Mute Triangle
            DrumPart.MISC			// Open Triangle
    };

    final Vec3 WRIST_LEFT = new Vec3(-50, -40, 0);
    final Vec3 WRIST_RIGHT = new Vec3(30, -40, 0);

    int songIndex = 0;
    List<DrumHit> song = new ArrayList<>();

    // Effect queue of size N
    HitEffect[] effects = new HitEffect[16];

    public Visualizer() throws Exception {
        // Load the drum kit image once when the panel is created
        try {
            drumKitImage = ImageIO.read(new File("src/main/resources/drums_600x600.png"));
        } catch (IOException e) {
            System.err.println("Failed to load drum kit image.");
            e.printStackTrace();
        }

        // Ensure drums are centered on-screen
        DRUM_X = (WIDTH - drumKitImage.getWidth()) / 2;
        DRUM_Y = (HEIGHT - drumKitImage.getHeight()) / 2;

        // Optional: Set panel size (or let JFrame pack it)
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.WHITE);


        ActionListener taskPerformer = new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                repaint();
            }
        };
        Timer timer = new Timer(REFRESH_RATE ,taskPerformer);
        timer.start();
        setup();
    }

    // Processes a midi file into drum instructions
    public void setup() throws Exception {

        // Initialize drumPoints
        drumPoints = new EnumMap<>(DrumPart.class);
        drumPoints.put(DrumPart.SNARE,	        new Vec3(-105, 45));
        drumPoints.put(DrumPart.HI_TOM,	        new Vec3(-70, 110));
        drumPoints.put(DrumPart.MID_TOM,	    new Vec3(25, 115));
        drumPoints.put(DrumPart.FLOOR_TOM,	    new Vec3(80, 30));
        drumPoints.put(DrumPart.HI_HAT,	        new Vec3(-185, 100));
        drumPoints.put(DrumPart.CRASH,	        new Vec3(-150, 170));
        drumPoints.put(DrumPart.RIDE,	        new Vec3(100, 130));
        drumPoints.put(DrumPart.CRASH_2,	    new Vec3(180, 140));
        drumPoints.put(DrumPart.KICK,	        new Vec3(-10, -60));
        drumPoints.put(DrumPart.HI_HAT_PEDAL,	new Vec3(-185, -120));
        drumPoints.put(DrumPart.MISC,	        new Vec3(-25, 150));

        // Set up midi file
        Sequence sequence = MidiSystem.getSequence(new File("src/main/resources/Rock3.mid"));
        MIDIFormatter.getInstance().setupFormatter(sequence);

        for (Track track: sequence.getTracks()) {
            TrackSettings settings = new TrackSettings();
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                settings.update(event.getMessage());
                if (settings.getChannel() != 9) continue;

                DrumHit newHit = toDrumHit(settings, event);
                if (newHit != null) song.add(newHit);
            }
        }

        programStartNanos = System.nanoTime();
    }

    @Override
    protected void paintComponent(Graphics G) {
        super.paintComponent(G);
        this.g = (Graphics2D) G;

        g.setStroke(new BasicStroke(1));

        if (drumKitImage != null) {
            g.drawImage(drumKitImage, DRUM_X, DRUM_Y, null);
        }

        drawDebugInfo(true, false);

        // End song 2 seconds after song is done
        if (getSongNanos() > song.getLast().startTimeNanos + 2e9)
            System.exit(0);

        // If the current note should be played,
        // advance until it's not time for current note OR song over
        while (songIndex < song.size() && getSongNanos() > song.get(songIndex).startTimeNanos) {
            addHitEffect(song.get(songIndex).getPoint());
            songIndex++;
        }
        updateHitEffects();
    }

    // Adds a hit effect in the first empty spot, if any
    private void addHitEffect(Vec3 loc) {
        for (int i = 0; i < effects.length; i++) {
            if (effects[i] == null) {
                effects[i] = new HitEffect(Vec3.random(10).add(loc), getSongNanos(), 400 * 1_000_000);
                break;
            }
        }
    }

    // Draws effects and deletes completed effects (set to null)
    private void updateHitEffects() {
        for (int i = 0; i < effects.length; i++) {
            HitEffect fx = effects[i];
            if (fx != null) {
                drawHitEffect(fx);
                if (getSongNanos() >= fx.startTick + fx.lengthNanos) {
                    effects[i] = null;
                }
            }
        }
    }

    // Converts a MidiEvent into a DrumHit
    private DrumHit toDrumHit(TrackSettings settings, MidiEvent event) {
        MidiMessage message = event.getMessage();
        if (message instanceof ShortMessage shortMessage
                && shortMessage.getCommand() == ShortMessage.NOTE_ON) {
                return new DrumHit(drumKeyMap[shortMessage.getData1() - 35],
                        settings.tickToNanos(event.getTick()));
        }
        return null;
    }

    // Draws a HitEffect object
    private void drawHitEffect(HitEffect fx) {
        double t = (double) (getSongNanos() - fx.startTick) / fx.lengthNanos;
        t = clamp(t, 0, 1);
        int alpha = (int)((1 - t) * 255);
        g.setColor(new Color(fx.color.getRed(), fx.color.getGreen(), fx.color.getBlue(), alpha));
        fillCircle(fx.loc, (int)(t * 50));
        g.setColor(new Color(0x00_C0_C0));
        drawCircle(fx.loc, (int)(t * 50));
    }

    // Draws a Stick object
    private void drawStick(Stick s) {
        g.setColor(Stick.COLOR);

        s.t = (double) getSongNanos() / (s.destPt.startTimeNanos - s.fromPt.startTimeNanos);
        Vec3 from = s.fromPt.getPoint();
        Vec3 dest = s.destPt.getPoint();

        Vec3 head = VisMath.bounce(s.t, from, dest);
        fillCircle(head, Stick.HEAD_RADIUS);

        g.setStroke(new BasicStroke(Stick.WIDTH));
        Vec3 dir = new Vec3(s.wrist).sub(head).normal();
        Vec3 pt2 = head.rayTo(dir, Stick.LENGTH);
        g.draw(new Line2D.Double(
                CENTER_X + head.x,
                CENTER_Y - head.y,
                CENTER_X + pt2.x,
                CENTER_Y - pt2.y
        ));
    }

    // Can draw a grid and point data on screen for debugging / development
    private void drawDebugInfo(boolean grid, boolean points) {
        // draw grid
        if (grid) {
            g.setColor(new Color(0x804080FF, true));
            int gridSpacing = 10;
            // draw horizontal lines
            for (int y = 0; y < HEIGHT; y += gridSpacing) {
                g.setStroke(new BasicStroke(y % (gridSpacing * 5) == 0 ? 2 : 1));
                g.drawLine(0, y, WIDTH, y);
            }
            // draw vertical lines
            for (int x = 0; x < WIDTH; x += gridSpacing) {
                g.setStroke(new BasicStroke(x % (gridSpacing * 5) == 0 ? 2 : 1));
                g.drawLine(x, 0, x, HEIGHT);
            }
        }

        // draw points (debug)
         if (points) {
            g.setColor(Color.YELLOW);
             for (Vec3 drumPoint : drumPoints.values())
                 fillCircle(drumPoint, 5);

            g.setColor(Color.RED);
            fillCircle(WRIST_LEFT, 5);
            fillCircle(WRIST_RIGHT, 5);

            g.setColor(Color.BLACK);
            fillCircle(new Vec3(), 8);
         }
    }

    // Fills a circle at the position vector pt with given radius
    private void fillCircle(Vec3 pt, int radius) {
        g.fillOval(
                CENTER_X - radius + (int)pt.x,
                CENTER_Y - radius - (int)pt.y,
                radius * 2,
                radius * 2
        );
    }

    // Draws a circle (outline only) at the position vector pt with given radius
    private void drawCircle(Vec3 pt, int radius) {
        g.drawOval(
                CENTER_X - radius + (int)pt.x,
                CENTER_Y - radius - (int)pt.y,
                radius * 2,
                radius * 2
        );
    }

    // Returns min or max if x is below or above range, otherwise x
    private double clamp(double x, double min, double max) {
        return Math.min(Math.max(min, x), max);
    }

    // Returns the current progress in the song in nanoseconds
    private long getSongNanos() {
        return System.nanoTime() - programStartNanos;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Drum Visualizer");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            try {
                frame.setContentPane(new Visualizer());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }



    private class DrumHit {
        public DrumPart drumPart;
        public long startTimeNanos;

        public DrumHit(DrumPart drumIndex, long startTimeNanos) {
            this.drumPart = drumIndex;
            this.startTimeNanos = startTimeNanos;
        }

        public Vec3 getPoint() {
            return drumPoints.get(drumPart);
        }
    }

    private class Stick {

        final static Color COLOR = new Color(0xDE_B8_87);
        final static int WIDTH = 5;
        final static int LENGTH = 50;
        final static int HEAD_RADIUS = 6;

        public Vec3 wrist;
        double t = 0;
        public DrumHit fromPt;
        public DrumHit destPt;

        public Stick(Vec3 wrist, DrumHit fromPt, DrumHit toPt) {
            this.wrist = wrist;
            this.fromPt = fromPt;
            this.destPt = toPt;
        }
    }

    private class HitEffect {
        public Color color;
        public Vec3 loc;
        public long startTick;
        public long lengthNanos;

        public HitEffect(Vec3 loc, long startTick, long lengthNanos) {
            this.loc = loc;
            this.startTick = startTick;
            this.lengthNanos = lengthNanos;
            this.color = new Color(0f, 1f, 1f);
        }
    }
}