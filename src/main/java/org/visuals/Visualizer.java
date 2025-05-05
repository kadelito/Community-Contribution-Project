package org.visuals;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.io.IOException;

public class Visualizer extends JPanel {

    private final int WIDTH = 800;
    private final int HEIGHT = 600;
    private final int CENTER_X = WIDTH / 2;
    private final int CENTER_Y = HEIGHT / 2;

    private BufferedImage drumKitImage;
    private final int DRUM_X;
    private final int DRUM_Y;

    private final int REFRESH_RATE = 15;

    public Visualizer() {
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

        programStart = System.currentTimeMillis();
        setup();
    }

    public void setup() {

    }

    long programStart;

    private final Vec3[] drumPoints = new Vec3[]{
            new Vec3(),             // Origin (0, 0)
            new Vec3(-105, 45),     // Snare
            new Vec3(-70, 110),     // Hi Tom
            new Vec3(25, 115),      // Mid Tom
            new Vec3(80, 30),       // Floor Tom
            new Vec3(-185, 100),    // Hi Hat
            new Vec3(-150, 170),    // Crash
            new Vec3(100, 130),     // Ride
            new Vec3(180, 140),     // Crash 2 (?)
            new Vec3(-10, -30)      // Kick
    };

    final int SNARE     = 1;
    final int HI_TOM    = 2;
    final int MID_TOM   = 3;
    final int FLOOR_TOM = 4;
    final int HI_HAT    = 5;
    final int CRASH     = 6;
    final int RIDE      = 7;
    final int CRASH_2   = 8;    // might end up unused
    final int KICK      = 9;

    final Vec3 WRIST_LEFT = new Vec3(-50, -40, 0);
    final Vec3 WRIST_RIGHT = new Vec3(30, -40, 0);

    int songIndex = 0;
    final DrumHit[] song = new DrumHit[]{
            new DrumHit(HI_HAT, 0),
            new DrumHit(KICK, 0),
            new DrumHit(HI_HAT, 500),
            new DrumHit(HI_HAT, 1000),
            new DrumHit(SNARE, 1000),
            new DrumHit(HI_HAT, 1500),
            new DrumHit(HI_HAT, 2000),
            new DrumHit(KICK, 2000),
            new DrumHit(HI_HAT, 2500),
            new DrumHit(HI_HAT, 3000),
            new DrumHit(SNARE, 3000),
            new DrumHit(HI_HAT, 3500),
            new DrumHit(HI_HAT, 4000),
            new DrumHit(KICK, 4000),
            new DrumHit(HI_HAT, 4500),
            new DrumHit(HI_HAT, 5000),
            new DrumHit(SNARE, 5000),
            new DrumHit(HI_HAT, 5500),
            new DrumHit(HI_HAT, 6000),
            new DrumHit(KICK, 6000),
            new DrumHit(HI_HAT, 6500),
            new DrumHit(SNARE, 7000),
            new DrumHit(SNARE, 7250),
            new DrumHit(SNARE, 7500),
            new DrumHit(SNARE, 7750),
            new DrumHit(CRASH, 8000),
            new DrumHit(KICK, 8000)
    };
    HitEffect[] effects = new HitEffect[10];
    private Graphics2D g;

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
        if (getSongMs() > song[song.length - 1].startTick + 2000)
            System.exit(0);

        while (songIndex < song.length && getSongMs() >= song[songIndex].startTick) {
            addHitEffect(song[songIndex].getPoint());
            songIndex++;
        }
        updateHitEffects();
    }

    // Adds a hit effect in the first empty spot, if any
    public void addHitEffect(Vec3 loc) {
        for (int i = 0; i < effects.length; i++) {
            if (effects[i] == null) {
                effects[i] = new HitEffect(loc, getSongMs(), 500);
                break;
            }
        }
    }

    // Draws effects and sets completed effects to 0
    public void updateHitEffects() {
        for (int i = 0; i < effects.length; i++) {
            HitEffect fx = effects[i];
            if (fx != null) {
                drawHitEffect(fx);
                if (getSongMs() >= fx.startTick + fx.tickLength)
                    effects[i] = null;
            }
        }
    }

    // Draws a HitEffect object
    public void drawHitEffect(HitEffect fx) {
        double t = (double) (getSongMs() - fx.startTick) / fx.tickLength;
        t = clamp(t, 0, 1);
        g.setColor(new Color(fx.color.getRed(), fx.color.getGreen(), fx.color.getBlue(), (int)((1 - t) * 255)));
        drawCircle(fx.loc, (int)(t * 50));
    }

    // Draws a Stick object
    private void drawStick(Stick s) {
        g.setColor(Stick.COLOR);

        s.t = (double) getSongMs() / (s.destPt.startTick - s.fromPt.startTick);
        Vec3 from = s.fromPt.getPoint();
        Vec3 dest = s.destPt.getPoint();

        Vec3 head = VisMath.bounce(s.t, from, dest);
        drawCircle(head, Stick.HEAD_RADIUS);

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
             for (Vec3 drumPoint : drumPoints)
                 drawCircle(drumPoint, 5);

            g.setColor(Color.RED);
            drawCircle(WRIST_LEFT, 5);
            drawCircle(WRIST_RIGHT, 5);

            g.setColor(Color.BLACK);
            drawCircle(new Vec3(), 8);
         }
    }

    // Draws a circle at the position vector pt with given radius
    private void drawCircle(Vec3 pt, int radius) {
        g.fillOval(
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

    // Returns the current progress in the song in milliseconds
    private long getSongMs() {
        return System.currentTimeMillis() - programStart;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Drum Visualizer");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new Visualizer());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }



    class DrumHit {
        public int drumIndex;   // references drumPoints
        public long startTick;  // in ms until next hit

        public DrumHit(int drumIndex, long startTick) {
            this.drumIndex = drumIndex;
            this.startTick = startTick;
        }

        public Vec3 getPoint() {
            return drumPoints[drumIndex];
        }
    }

    class Stick {

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

    class HitEffect {
        public Color color;
        public Vec3 loc;
        public long startTick;
        public long tickLength;

        public HitEffect(Vec3 loc, long startTick, long tickLength) {
            this.loc = loc;
            this.startTick = startTick;
            this.tickLength = tickLength;
            this.color = new Color(0f, 1f, 1f);
        }
    }
}