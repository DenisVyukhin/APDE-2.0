ArrayList<Particle> particles;

float gravityStrength = 0.18;
float friction = 0.985;
float touchForce = 1.8;

boolean attractMode = true;


void setup() {
   fullScreen();
   orientation(PORTRAIT);

   particles = new ArrayList<Particle>();

   for (int i = 0; i < 100; i++) {
      particles.add(new Particle(random(width), random(height)));
   }
}


void draw() {
   background(8, 12, 22);

   // Touch field
   if (mousePressed) {
      noFill();
      stroke(80, 180, 255, 70);
      strokeWeight(width * 0.004);
      ellipse(mouseX, mouseY, width * 0.28, width * 0.28);
   }

   // Lines between close particles
   drawConnections();

   // Particles
   for (int i = 0; i < particles.size(); i++) {
      Particle p = particles.get(i);

      if (mousePressed) {
         p.applyTouch(mouseX, mouseY);
      }

      p.update();
      p.edges();
      p.display();
   }

   drawUI();
}


void drawConnections() {
   float maxDist = width * 0.13;

   strokeWeight(width * 0.0015);

   for (int i = 0; i < particles.size(); i++) {
      Particle a = particles.get(i);

      for (int j = i + 1; j < particles.size(); j++) {
         Particle b = particles.get(j);

         float d = dist(a.x, a.y, b.x, b.y);

         if (d < maxDist) {
            float alpha = map(d, 0, maxDist, 90, 0);
            stroke(80, 180, 255, alpha);
            line(a.x, a.y, b.x, b.y);
         }
      }
   }
}


void drawUI() {
   fill(255, 230);
   textAlign(LEFT, TOP);
   textSize(width * 0.045);
   text("Particle Playground", width * 0.05, height * 0.04);

   fill(255, 140);
   textSize(width * 0.03);
   text("Tap to attract / Double tap to switch mode", width * 0.05, height * 0.085);

   fill(attractMode ? color(80, 220, 255) : color(255, 90, 130));
   textSize(width * 0.032);
   text(attractMode ? "Mode: Attract" : "Mode: Repel", width * 0.05, height * 0.125);
}


void mousePressed() {
   if (millis() - lastTapTime < 280) {
      attractMode = !attractMode;
   }

   lastTapTime = millis();
}

int lastTapTime = 0;


class Particle {
   float x;
   float y;
   float vx;
   float vy;
   float size;
   float hueOffset;

   Particle(float startX, float startY) {
      x = startX;
      y = startY;

      vx = random(-1.5, 1.5);
      vy = random(-1.5, 1.5);

      size = random(width * 0.012, width * 0.028);
      hueOffset = random(255);
   }

   void applyTouch(float tx, float ty) {
      float dx = tx - x;
      float dy = ty - y;
      float d = sqrt(dx * dx + dy * dy);

      if (d < 1) {
         d = 1;
      }

      float range = width * 0.38;

      if (d < range) {
         float power = map(d, 0, range, touchForce, 0);

         dx /= d;
         dy /= d;

         if (!attractMode) {
            power *= -1;
         }

         vx += dx * power;
         vy += dy * power;
      }
   }

   void update() {
      vx *= friction;
      vy *= friction;

      x += vx;
      y += vy;
   }

   void edges() {
      if (x < size) {
         x = size;
         vx *= -0.8;
      }

      if (x > width - size) {
         x = width - size;
         vx *= -0.8;
      }

      if (y < size) {
         y = size;
         vy *= -0.8;
      }

      if (y > height - size) {
         y = height - size;
         vy *= -0.8;
      }
   }

   void display() {
      noStroke();

      float pulse = sin(frameCount * 0.04 + hueOffset) * 0.5 + 0.5;
      float glowSize = size * map(pulse, 0, 1, 2.2, 3.8);

      fill(60, 160, 255, 28);
      ellipse(x, y, glowSize, glowSize);

      fill(120, 220, 255, 210);
      ellipse(x, y, size, size);

      fill(255, 240);
      ellipse(x - size * 0.18, y - size * 0.18, size * 0.25, size * 0.25);
   }
}