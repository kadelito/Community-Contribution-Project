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

        lastHitStartTime = System.currentTimeMillis();
    }

    private final Vec3[] drumPoints = new Vec3[]{
            new Vec3(),             // Origin (0, 0)
            new Vec3(-105, 45),     // Snare
            new Vec3(-70, 110),     // Hi Tom
            new Vec3(25, 115),      // Mid Tom
            new Vec3(80, 30),       // Floor Tom
            new Vec3(-185, 100),    // Hi Hat
            new Vec3(-150, 170),    // Crash
            new Vec3(100, 130),     // Ride
            new Vec3(180, 140),     // Crash 2
    };

    final int SNARE     = 1;
    final int HI_TOM    = 2;
    final int MID_TOM   = 3;
    final int FLOOR_TOM = 4;
    final int HI_HAT    = 5;
    final int CRASH     = 6;
    final int RIDE      = 7;
    final int CRASH_2   = 8;

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;
        g2.setStroke(new BasicStroke(1));

        if (drumKitImage != null) {
            g2.drawImage(drumKitImage, DRUM_X, DRUM_Y, null);
        }

        drawDebugInfo(g2, false, true);

        // update if current hit is over
        long runningMs = (System.currentTimeMillis() - lastHitStartTime);
        if (runningMs >= from.hitLength) {
            from = instructions[index];
            index = (index + 1) % instructions.length;
            lastHitStartTime = System.currentTimeMillis();
            runningMs = 0;
        }

        // get stick head position
        DrumHit next = instructions[index];
        double t = runningMs / from.hitLength;
        Vec3 head = VisMath.bounce(t, getPoint(from), getPoint(next));
        Vec3 dir = new Vec3(WRIST1).sub(head).normal();

        // draw stick
        g2.setColor(new Color(0xA0A040));
        g2.setStroke(new BasicStroke(5));

        drawCircle(g2, head, 6);
        drawRay(g2, head, dir, 60);
    }

    int index = 0;
    long lastHitStartTime;
    final DrumHit[] instructions = new DrumHit[]{
            new DrumHit(HI_HAT, 250),
            new DrumHit(HI_HAT, 250),
            new DrumHit(SNARE, 250),
            new DrumHit(HI_HAT, 250),
    };
    DrumHit from = new DrumHit(0, 1000);

    final Vec3 WRIST1 = new Vec3(-50, -40, 0);
    final Vec3 WRIST2 = new Vec3(30, -40, 0);

    private Vec3 getPoint(DrumHit d) {
        return drumPoints[d.drumIndex];
    }

    private void drawDebugInfo(Graphics2D g, boolean grid, boolean points) {
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
            for (int i = 0; i < drumPoints.length; i++)
                drawCircle(g, drumPoints[i], 5);

            g.setColor(Color.RED);
            drawCircle(g, WRIST1, 5);
            drawCircle(g, WRIST2, 5);

            g.setColor(Color.BLACK);
            drawCircle(g, new Vec3(), 8);
         }
    }

    private void drawCircle(Graphics g, Vec3 pt, int radius) {
        g.fillOval(
                CENTER_X - radius + (int)pt.x,
                CENTER_Y - radius - (int)pt.y,
                radius * 2,
                radius * 2
        );
    }

    private void drawRay(Graphics2D g, Vec3 pt, Vec3 dir, double len) {
        Vec3 pt2 = pt.rayTo(dir, len);
        g.draw(new Line2D.Double(
                CENTER_X + pt.x,
                CENTER_Y - pt.y,
                CENTER_X + pt2.x,
                CENTER_Y - pt2.y
        ));
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
}

class DrumHit {
    public int drumIndex;       // references drumPoints, will be changed later
    public double hitLength;    // in ms until next hit

    public DrumHit(int drumIndex, double hitLength) {
        this.drumIndex = drumIndex;
        this.hitLength = hitLength;
    }
}