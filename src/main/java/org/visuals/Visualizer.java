package org.visuals;

import org.audio.MusicHandler;
import org.midireading.MIDIFormatter;
import org.midireading.TrackSettings;

import javax.sound.midi.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
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

    private final BufferedImage drumKitImage;
    private final int DRUM_X;
    private final int DRUM_Y;

    private final int REFRESH_RATE = 16;

    private Graphics2D g;

    /*====================== Actual visualizer stuff ======================*/

    private final double SLOW_FACTOR = 1;

    private final String songPathname = "src/main/resources/AmericanIdiot.mid";

    Map<DrumPart, Vec3> drumPoints;
    static final Vec3 WRIST_LEFT = new Vec3(-50, -40);
    static final Vec3 WRIST_RIGHT = new Vec3(30, -40);
    Stick leftStick  = new Stick(WRIST_LEFT, null);
    Stick rightStick = new Stick(WRIST_RIGHT, null);

    static final Vec3 METRONOME_PIVOT = new Vec3(-25, 250);
    static final double METRONOME_LENGTH = 40;
    Vec3 metronome_swing = new Vec3();
    int[] metronome_corners_x;
    int[] metronome_corners_y;

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
            DrumPart.HI_HAT,		// Closed Hi Hat
            DrumPart.FLOOR_TOM,		// High Floor Tom
            DrumPart.HI_HAT_PEDAL,	// Pedal Hi-Hat
            DrumPart.FLOOR_TOM,		// Low Tom
            DrumPart.HI_HAT,		// Open Hi-Hat
            DrumPart.MID_TOM,		// Low-Mid Tom
            DrumPart.MID_TOM,		// Hi Mid Tom
            DrumPart.CRASH,			// Crash Cymbal 1
            DrumPart.HI_TOM,		// High Tom
            DrumPart.RIDE,			// Ride Cymbal 1
            DrumPart.CRASH,			// Chinese Cymbal
            DrumPart.RIDE,			// Ride Bell
            DrumPart.HI_HAT,		// Tambourine
            DrumPart.CRASH_2,		// Splash Cymbal
            DrumPart.MISC,			// Cowbell
            DrumPart.CRASH_2,		// Crash Cymbal 2
            DrumPart.MISC,			// Vibraslap
            DrumPart.RIDE,			// Ride Cymbal 2
            DrumPart.HI_TOM,		// Hi Bongo
            DrumPart.MID_TOM,		// Low Bongo
            DrumPart.HI_TOM,		// Mute Hi Conga
            DrumPart.HI_TOM,		// Open Hi Conga
            DrumPart.MID_TOM,		// Low Conga
            DrumPart.HI_TOM,		// High Timbale
            DrumPart.MID_TOM,		// Low Timbale
            DrumPart.MISC,			// High Agogo
            DrumPart.MISC,			// Low Agogo
            DrumPart.HI_HAT,		// Cabasa
            DrumPart.HI_HAT,		// Maracas
            DrumPart.MISC,			// Short Whistle
            DrumPart.MISC,			// Long Whistle
            DrumPart.MISC,			// Short Guiro
            DrumPart.MISC,			// Long Guiro
            DrumPart.MISC,			// Claves
            DrumPart.MISC,			// Hi Wood Block
            DrumPart.MISC,			// Low Wood Block
            DrumPart.HI_TOM,		// Mute Cuica
            DrumPart.HI_TOM,		// Open Cuica
            DrumPart.MISC,			// Mute Triangle
            DrumPart.MISC			// Open Triangle
    };

    // Song stuff
    Sequence sequence;
    int songIndex = 0;
    List<DrumHit> song = new ArrayList<>();

    // Limited size effect queue (any extra are ignored)
    HitEffect[] effects = new HitEffect[16];

    long nextBeatNanos = 0;
    long deltaBeatNanos;
    long programStartNanos;

    private Visualizer() throws Exception {
        // Load the drum kit image once when the panel is created
        try {
            drumKitImage = ImageIO.read(new File("src/main/resources/drums_600x600.png"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load drum kit image.");
        }

        // Ensure drums are centered on-screen
        DRUM_X = (WIDTH - drumKitImage.getWidth(null)) / 2;
        DRUM_Y = (HEIGHT - drumKitImage.getHeight(null)) / 2;

        // Optional: Set panel size (or let JFrame pack it)
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.WHITE);

        // Add KeyListener for early escaping [ esc ]
        this.addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                // Check for Escape key press
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    System.exit(0);  // Exit the program
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {}
            @Override
            public void keyTyped(KeyEvent e) {
            }
        });

        // Make the panel focusable to capture key events
        setFocusable(true);
        requestFocusInWindow();  // Request focus explicitly so it can capture key events

        MusicHandler music = new MusicHandler(songPathname, SLOW_FACTOR);
        setup();

        // Set up a timer for music playback and refreshing the display
        ActionListener taskPerformer = event -> {
            music.loop();
            repaint();
            music.endIfOver();
        };
        Timer timer = new Timer(REFRESH_RATE, taskPerformer);

        programStartNanos = System.nanoTime();

        timer.start();
    }

    // Processes a midi file into drum instructions
    public void setup() throws Exception {

        // Initialize points that need to be initialized
        metronome_corners_x = new int[]{
                CENTER_X + (int) (METRONOME_PIVOT.x),
                CENTER_X + (int) (METRONOME_PIVOT.x - 0.6 * METRONOME_LENGTH - 10),
                CENTER_X + (int) (METRONOME_PIVOT.x + 0.6 * METRONOME_LENGTH + 10),
        };
        metronome_corners_y = new int[]{
                CENTER_Y - (int) (METRONOME_PIVOT.y + 20),
                CENTER_Y - (int) (METRONOME_PIVOT.y - METRONOME_LENGTH - 10),
                CENTER_Y - (int) (METRONOME_PIVOT.y - METRONOME_LENGTH - 10),
        };
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
        drumPoints.put(DrumPart.MISC,	        new Vec3(-225, 15));

        // Set up midi file
        sequence = MidiSystem.getSequence(new File(songPathname));
        if (sequence.getDivisionType() != Sequence.PPQ)
            throw new UnsupportedOperationException("Non-PPQ division is supported!");
        MIDIFormatter.getInstance().setupFormatter(sequence);

        // Convert channel 10 (9 in 0-indexed) to drum hits
        TrackSettings settings = new TrackSettings();
        List<DrumHit> leftHits = new ArrayList<>();
        leftHits.add(new DrumHit(DrumPart.KICK, 0));
        List<DrumHit> rightHits = new ArrayList<>();
        rightHits.add(new DrumHit(DrumPart.KICK, 0));
        for (Track track: sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                settings.update(event.getMessage());
                if (settings.getChannel() != 9) continue;

                DrumHit newHit = toDrumHit(settings, event);
                if (newHit != null) {
                    song.add(newHit);
                    int result = evalDrumHit(newHit);
                    switch (result) {
                        case 1:
                            leftHits.add(newHit);
                            break;

                        case 2:
                            rightHits.add(newHit);
                            break;
                    }
                }
            }
        }
        deltaBeatNanos = (long) (SLOW_FACTOR * settings.tickToNanos(sequence.getResolution()));
        leftHits.add(new DrumHit(DrumPart.KICK, Long.MAX_VALUE));
        leftStick.hits = leftHits.toArray(new DrumHit[0]);
        rightHits.add(new DrumHit(DrumPart.KICK, Long.MAX_VALUE));
        rightStick.hits = rightHits.toArray(new DrumHit[0]);
    }

    final static DrumPart[] leftParts = new DrumPart[]{
            DrumPart.SNARE, DrumPart.HI_TOM, DrumPart.MID_TOM, DrumPart.MISC
    };
    final static DrumPart[] rightParts = new DrumPart[]{
            DrumPart.FLOOR_TOM, DrumPart.HI_HAT, DrumPart.CRASH, DrumPart.CRASH_2, DrumPart.RIDE
    };

    // Returns a number 0 - 2 inclusive whether a DrumHit should be left (1), right (2), or neither (0)
    private int evalDrumHit(DrumHit hit) {
        for (DrumPart part: leftParts) {
            if (hit.drumPart == part)
                return 1;
        }
        for (DrumPart part: rightParts) {
            if (hit.drumPart == part)
                return 2;
        }
        return 0;
    }

    // Converts a MidiEvent into a DrumHit
    private DrumHit toDrumHit(TrackSettings settings, MidiEvent event) {
        MidiMessage message = event.getMessage();
        if (message instanceof ShortMessage shortMessage
                && shortMessage.getCommand() == ShortMessage.NOTE_ON) {
            return new DrumHit(drumKeyMap[shortMessage.getData1() - 35],
                    (long) (settings.tickToNanos(event.getTick()) * SLOW_FACTOR));
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics G) {
        super.paintComponent(G);
        this.g = (Graphics2D) G;

        if (drumKitImage != null) {
            g.drawImage(drumKitImage, DRUM_X, DRUM_Y, null);
        }

        drawDebugInfo(false, true);

        // Draw metronome & pulse
        drawMetronome();
        if (getSongNanos() > nextBeatNanos) {
            addHitEffect(new Vec3(metronome_swing));
            nextBeatNanos += deltaBeatNanos;
        }

        // Draw all hit effects
        while (songIndex < song.size() && getSongNanos() > song.get(songIndex).startTimeNanos) {
            addHitEffect(song.get(songIndex).getPoint());
            songIndex++;
        }

        // Draw both sticks
//        updateStick(leftStick);
//        drawStick(leftStick);
//        updateStick(rightStick);
//        drawStick(rightStick);
        // commented out bc not work :(

        updateHitEffects();
    }

    // Inserts an immediate hit effect in the first empty spot, if any
    private void addHitEffect(Vec3 loc) {
        for (int i = 0; i < effects.length; i++) {
            if (effects[i] == null) {
                effects[i] = new HitEffect(loc, getSongNanos(), 300_000_000);
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

    private void drawMetronome() {
        // Draw metronome back
        g.setColor(new Color(0x867342));
        g.fillPolygon(metronome_corners_x, metronome_corners_y, 3);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2));
        g.drawPolygon(metronome_corners_x, metronome_corners_y, 3);

        // Get position of swing
        double angle = (Math.PI / 8) * Math.cos(Math.PI * getSongNanos() / deltaBeatNanos - 0.25);
        metronome_swing.x = Math.sin(angle);
        metronome_swing.y = -Math.cos(angle);
        metronome_swing.scale(METRONOME_LENGTH).add(METRONOME_PIVOT);

        // Draw string, pivot & swing
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2));
        g.drawLine(
                CENTER_X + (int) METRONOME_PIVOT.x,
                CENTER_Y - (int) METRONOME_PIVOT.y,
                CENTER_X + (int) metronome_swing.x,
                CENTER_Y - (int) metronome_swing.y
        );
        g.setColor(Color.LIGHT_GRAY);
        fillCircle(METRONOME_PIVOT, 4);
        fillCircle(metronome_swing, 5);
    }

    // Draws a HitEffect object
    private void drawHitEffect(HitEffect fx) {
        double t = (double) (getSongNanos() - fx.startTick) / fx.lengthNanos;
        t = clamp(t, 0, 1);
        int alpha = (int)((1 - t) * 255);

        g.setStroke(new BasicStroke(1));
        g.setColor(new Color(fx.color.getRed(), fx.color.getGreen(), fx.color.getBlue(), alpha));
        fillCircle(fx.loc, (int)(t * 50));
        g.setColor(new Color(0x00_00_00));
        drawCircle(fx.loc, (int)(t * 50));
    }

    // Returns min or max if x is below or above range, otherwise x
    private double clamp(double x, double min, double max) {
        return Math.min(Math.max(min, x), max);
    }

    // Updates a Stick object
    private void updateStick (Stick s) {
        DrumHit destHit = s.hits[s.index - 1];
        DrumHit fromHit = s.hits[s.index];
        s.t = (double)(getSongNanos() - fromHit.startTimeNanos) / (destHit.startTimeNanos - fromHit.startTimeNanos);
        assert 0 <= s.t && s.t <= 1;
        if (getSongNanos() > destHit.startTimeNanos) s.index++;
    }

    // Draws a Stick object
    private void drawStick(Stick s) {
        g.setColor(Stick.COLOR);

        Vec3 from = s.hits[s.index - 1].getPoint();
        Vec3 dest = s.hits[s.index].getPoint();

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
            fillCircle(METRONOME_PIVOT, 5);

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

    // Returns the current progress in the song in nanoseconds
    private long getSongNanos() {
        return (System.nanoTime() * 1) - programStartNanos;
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
        public int index = 1;
        public DrumHit[] hits;

        public Stick(Vec3 wrist, DrumHit[] hits) {
            this.wrist = wrist;
            this.hits = hits;
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
            this.color = new Color(0xFF_FF_C0);
        }
    }
}