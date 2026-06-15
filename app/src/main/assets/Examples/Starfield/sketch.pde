ArrayList<Star> stars;

float warpSpeed;
float targetWarpSpeed;

boolean hyperMode = false;


void setup() {
   fullScreen();
   orientation(PORTRAIT);

   stars = new ArrayList<Star>();

   for (int i = 0; i < 900; i++) {
      stars.add(new Star());
   }

   warpSpeed = 12;
   targetWarpSpeed = 12;
}


void draw() {
   background(2, 4, 10);

   updateWarp();

   translate(width * 0.5, height * 0.5);

   for (int i = 0; i < stars.size(); i++) {
      Star s = stars.get(i);

      s.update();
      s.display();
   }

   resetMatrix();

   drawGlow();
   drawUI();
}


void updateWarp() {
   if (mousePressed) {
      targetWarpSpeed = 58;
      hyperMode = true;
   } else {
      targetWarpSpeed = 12;
      hyperMode = false;
   }

   warpSpeed = lerp(warpSpeed, targetWarpSpeed, 0.05);
}


void drawGlow() {
   noStroke();

   fill(80, 140, 255, 16);
   ellipse(width * 0.5, height * 0.5, width * 0.75, width * 0.75);

   fill(140, 200, 255, 10);
   ellipse(width * 0.5, height * 0.5, width * 1.2, width * 1.2);
}


void drawUI() {
   fill(255, 230);

   textAlign(LEFT, TOP);
   textSize(width * 0.045);

   text("Starfield Warp", width * 0.05, height * 0.04);

   fill(255, 135);

   textSize(width * 0.03);

   text("Hold screen for hyperspeed", width * 0.05, height * 0.085);

   fill(hyperMode ? color(120, 220, 255) : color(180));

   textSize(width * 0.032);

   text(hyperMode ? "HYPER MODE" : "Cruise Mode", width * 0.05, height * 0.125);
}


class Star {
   float x;
   float y;
   float z;

   float prevX;
   float prevY;

   Star() {
      reset();
   }

   void reset() {
      x = random(-width, width);
      y = random(-height, height);

      z = random(width);

      prevX = x;
      prevY = y;
   }

   void update() {
      prevX = x / z * width;
      prevY = y / z * width;

      z -= warpSpeed;

      if (z < 1) {
         reset();
         z = width;
      }
   }

   void display() {
      float sx = x / z * width;
      float sy = y / z * width;

      float radiusValue = map(z, 0, width, width * 0.018, width * 0.001);

      float alphaValue = map(z, 0, width, 255, 40);

      float px = prevX;
      float py = prevY;

      stroke(120, 200, 255, alphaValue);
      strokeWeight(radiusValue * 0.55);

      line(px, py, sx, sy);

      noStroke();

      fill(180, 230, 255, alphaValue * 0.12);
      ellipse(sx, sy, radiusValue * 4.5, radiusValue * 4.5);

      fill(255, alphaValue);
      ellipse(sx, sy, radiusValue, radiusValue);
   }
}