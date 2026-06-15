float BASE_W = 300;
float BASE_H = 600;
float sceneScale;
float sceneX;
float sceneY;
float worldMouseX;
float worldMouseY;
float landerX;
float landerY;
float velocityX;
float velocityY;
float padX = 150;
int state = 0;
String message = "";
Smoke[] smoke = new Smoke[55];
int smokeIndex = 0;
int resultFrame = 0;

void setup() {
   fullScreen();
   //size(30, 60);
   smooth();
   textAlign(CENTER, CENTER);

   for (int i = 0; i < smoke.length; i++) {
      smoke[i] = new Smoke();
   }

   resetGame();
}

void draw() {
   updateScene();
   background(5, 6, 18);
   beginScene();
   drawSpace();
   updateLander();
   drawPad();
   updateSmoke();
   drawLander();
   drawHud();
   endScene();
}

void updateScene() {
   sceneScale = min(width / BASE_W, height / BASE_H);
   sceneX = (width - BASE_W * sceneScale) * 0.5;
   sceneY = (height - BASE_H * sceneScale) * 0.5;
   worldMouseX = constrain((mouseX - sceneX) / sceneScale, 0, BASE_W);
   worldMouseY = constrain((mouseY - sceneY) / sceneScale, 0, BASE_H);
}

void beginScene() {
   pushMatrix();
   translate(sceneX, sceneY);
   scale(sceneScale);
}

void endScene() {
   popMatrix();
}

void resetGame() {
   landerX = 150;
   landerY = 80;
   velocityX = random(-0.8, 0.8);
   velocityY = 0;
   padX = random(72, 228);
   state = 0;
   message = "";
   resultFrame = 0;

   for (int i = 0; i < smoke.length; i++) {
      smoke[i].active = false;
   }
}

void drawSpace() {
   noStroke();

   for (int y = 0; y < BASE_H; y += 10) {
      float t = y / BASE_H;
      fill(5 + 20 * t, 6 + 16 * t, 18 + 32 * t);
      rect(0, y, BASE_W, 10);
   }

   for (int i = 0; i < 42; i++) {
      float x = (i * 73) % 300;
      float y = (i * 137) % 455;

      float twinkle = noise(i * 8.7, frameCount * 0.025);
      twinkle = pow(twinkle, 1.7);

      float size = 1.4 + (i % 3);
      float alpha = twinkle * 245;

      fill(255, alpha);
      ellipse(x, y, size, size);
   }

   fill(30, 35, 48);
   beginShape();
   vertex(0, 540);

   for (int x = 0; x <= BASE_W; x += 30) {
      vertex(x, 520 + noise(x * 0.04) * 36);
   }

   vertex(BASE_W, BASE_H);
   vertex(0, BASE_H);
   endShape(CLOSE);
}

void updateLander() {
   if (state != 0) {
      return;
   }

   velocityY += 0.045;

   if (mousePressed) {
      float aim = constrain((worldMouseX - landerX) * 0.002, -0.035, 0.035);
      velocityX += aim;
      velocityY -= 0.105;

      if (frameCount % 2 == 0) {
         addSmoke(landerX, landerY + 24);
      }
   }

   velocityX *= 0.994;
   landerX += velocityX;
   landerY += velocityY;

   if (landerX < 16 || landerX > BASE_W - 16) {
      velocityX *= -0.55;
      landerX = constrain(landerX, 16, BASE_W - 16);
   }

   if (landerY < 28) {
      landerY = 28;
      velocityY = max(velocityY, 0.25);
   }

   if (landerY > 518) {
      boolean onPad = abs(landerX - padX) < 38;
      boolean soft = abs(velocityX) < 1.15 && velocityY < 1.65;

      state = (onPad && soft) ? 1 : 2;
      message = (state == 1) ? "Landed 🚀" : "Crashed 💥";
      resultFrame = frameCount;
      landerY = 518;
   }
}

void addSmoke(float x, float y) {
   smoke[smokeIndex].start(x + random(-5, 5), y + random(-2, 4), velocityX);
   smokeIndex = (smokeIndex + 1) % smoke.length;
}

float groundAt(float x) {
   return 520 + noise(constrain(x, 0, BASE_W) * 0.04) * 36;
}

void updateSmoke() {
   for (int i = 0; i < smoke.length; i++) {
      smoke[i].update();
      smoke[i].show();
   }
}

void drawPad() {
   noStroke();
   fill(80, 210, 180);
   rect(padX - 45, 530, 90, 8, 4);

   fill(80, 210, 180, 70);
   rect(padX - 55, 538, 110, 6, 3);
}

void drawLander() {
   pushMatrix();
   translate(landerX, landerY);
   rotate(velocityX * 0.12);

   noStroke();
   fill(220);
   quad(-13, -14, 13, -14, 18, 10, -18, 10);

   fill(80, 150, 255);
   ellipse(0, -5, 15, 15);

   stroke(210);
   strokeWeight(2);
   line(-11, 9, -20, 23);
   line(11, 9, 20, 23);
   line(-20, 23, -10, 23);
   line(20, 23, 10, 23);

   if (mousePressed && state == 0) {
      noStroke();
      fill(255, 160, 40, 220);
      triangle(-7, 10, 7, 10, 0, 34 + random(5));

      fill(255, 240, 120, 190);
      triangle(-4, 10, 4, 10, 0, 26 + random(4));
   }

   popMatrix();
}

void drawHud() {
   fill(255, 230);
   textSize(15);
   text("Speed " + nf(velocityY, 1, 2), 70, 28);
   text("Hold for thrust", 210, 28);

   if (state != 0) {
      float appear = constrain((frameCount - resultFrame) / 28.0, 0, 1);
      float softAppear = 1 - pow(1 - appear, 3);
      float textAppear = constrain((frameCount - resultFrame - 8) / 22.0, 0, 1);
      float panelY = lerp(266, 240, softAppear);
      float panelScale = lerp(0.88, 1.0, softAppear);

      int glowColor;
      if (state == 1) {
         glowColor = color(90, 235, 135);
      } else {
         glowColor = color(255, 90, 95);
      }

      noStroke();
      fill(0, 175 * softAppear);
      rect(0, 0, BASE_W, BASE_H);

      pushMatrix();
      translate(150, panelY + 56);
      scale(panelScale);

      noStroke();
      fill(red(glowColor), green(glowColor), blue(glowColor), 18 * softAppear);
      rect(-136, -74, 272, 148, 28);

      fill(red(glowColor), green(glowColor), blue(glowColor), 34 * softAppear);
      rect(-126, -66, 252, 132, 22);

      fill(8, 12, 24, 230 * softAppear);
      stroke(red(glowColor), green(glowColor), blue(glowColor), 230 * softAppear);
      strokeWeight(2);
      rect(-115, -56, 230, 112, 9);

      noStroke();
      fill(red(glowColor), green(glowColor), blue(glowColor), 255 * textAppear);
      textSize(26);
      text(message, 0, -14);

      fill(255, 230 * textAppear);
      textSize(15);
      text("Tap to restart", 0, 22);

      popMatrix();
   }
}

void mousePressed() {
   if (state != 0) {
      resetGame();
   }
}

class Smoke {
   float x;
   float y;
   float vx;
   float vy;
   float size;
   float life;
   boolean active;

   void start(float newX, float newY, float landerSpeedX) {
      x = newX;
      y = newY;
      vx = random(-0.45, 0.45) + landerSpeedX * 0.25;
      vy = random(0.9, 1.9);
      size = random(7, 13);
      life = 1;
      active = true;
   }

   void update() {
      if (!active) {
         return;
      }

      x += vx;
      y += vy;
      vx += random(-0.025, 0.025);
      vy *= 0.985;
      size += 0.42;
      life -= 0.018;

      if (y + size * 0.35 > groundAt(x)) {
         y = groundAt(x) - size * 0.35;
         vy = 0;
         vx *= 0.94;
         size += 0.18;
         life -= 0.008;
      }

      if (life <= 0) {
         active = false;
      }
   }

   void show() {
      if (!active) {
         return;
      }

      noStroke();
      fill(185, 195, 205, 95 * life);
      ellipse(x, y, size * 1.15, size);

      fill(110, 120, 135, 45 * life);
      ellipse(x - 2, y + 2, size * 0.75, size * 0.62);
   }
}