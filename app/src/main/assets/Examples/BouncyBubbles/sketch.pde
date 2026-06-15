ArrayList<Ball> balls = new ArrayList<Ball>();

float gravity = 0.35;

void setup() {
   fullScreen(P2D);
   orientation(PORTRAIT);

   colorMode(HSB, 360, 100, 100, 100);
   smooth(8);
   noStroke();

   createBalls();
}


void draw() {
   background(235, 45, 6);

   updateBalls();
   drawHint();
}


// Setup

void createBalls() {
   balls.clear();

   for (int i = 0; i < 10; i++) {
      balls.add(new Ball(
         random(width * 0.15, width * 0.85),
         random(height * 0.1, height * 0.55),
         random(width * 0.06, width * 0.12)
      ));
   }
}


// Input

void mousePressed() {
   balls.add(new Ball(mouseX, mouseY, random(width * 0.07, width * 0.12)));

   while (balls.size() > 34) {
      balls.remove(0);
   }
}


// Balls

void updateBalls() {
   blendMode(ADD);

   for (int i = 0; i < balls.size(); i++) {
      Ball b = balls.get(i);
      b.update();
      b.show();
   }

   blendMode(BLEND);
}


// UI

void drawHint() {
   fill(0, 0, 100, 55);
   textAlign(CENTER, CENTER);
   textSize(width * 0.04);
   text("Tap to spawn glowing balls", width / 2, height / 2);
}


// Ball class

class Ball {
   float x;
   float y;
   float vx;
   float vy;
   float r;
   float hue;

   Ball(float x, float y, float r) {
      this.x = x;
      this.y = y;
      this.r = r;

      vx = random(-4, 4);
      vy = random(-2, 2);
      hue = random(160, 320);
   }

   void update() {
      vy += gravity;

      x += vx;
      y += vy;

      if (x < r) {
         x = r;
         vx *= -0.88;
      }

      if (x > width - r) {
         x = width - r;
         vx *= -0.88;
      }

      if (y < r) {
         y = r;
         vy *= -0.8;
      }

      if (y > height - r) {
         y = height - r;
         vy *= -0.82;
         vx *= 0.985;
      }

      collide();
   }

   void collide() {
      for (int i = 0; i < balls.size(); i++) {
         Ball other = balls.get(i);

         if (other == this) continue;

         float dx = other.x - x;
         float dy = other.y - y;
         float d = sqrt(dx * dx + dy * dy);
         float minD = r + other.r;

         if (d > 0 && d < minD) {
            float overlap = (minD - d) * 0.5;
            float nx = dx / d;
            float ny = dy / d;

            x -= nx * overlap;
            y -= ny * overlap;
            other.x += nx * overlap;
            other.y += ny * overlap;

            float bounce = 0.45;
            vx -= nx * bounce;
            vy -= ny * bounce;
            other.vx += nx * bounce;
            other.vy += ny * bounce;
         }
      }
   }

   void show() {
      for (int i = 4; i >= 1; i--) {
         fill(hue, 80, 100, 4);
         ellipse(x, y, r * i * 2.2, r * i * 2.2);
      }

      fill(hue, 70, 100, 90);
      ellipse(x, y, r * 2, r * 2);

      fill(0, 0, 100, 55);
      ellipse(x - r * 0.35, y - r * 0.35, r * 0.5, r * 0.5);
   }
}