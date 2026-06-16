PGraphics img;

color[] touchColors = {
   color(0, 150, 255), color(255, 100, 0), color(50, 255, 100), color(255, 0, 200), color(100, 0, 255)
};

void setup() {
   fullScreen(P2D);

   img = createGraphics(int(width / 25f), int(height / 25f));

   img.beginDraw();
   img.background(0);
   img.endDraw();
}

void draw() {
   background(0);

   img.beginDraw();
   img.noStroke();

   for(int i = 0; i < touches.length; i ++) {
      float x = touches[i].x / 25f;
      float y = touches[i].y / 25f;

      if(i < touchColors.length) {
         img.fill(touchColors[i]);
      }

      img.ellipse(x, y, img.width / 3f, img.width / 3f);
      img.fill(255);
      img.ellipse(x, y, img.width / 6f, img.width / 6f);
   }

   img.endDraw();
   img.filter(BLUR, 1);

   blendMode(ADD);

   image(img, 0, 0, width, height);

   fill(255);

   for(int i = 0; i < touches.length; i ++) {
      ellipse(touches[i].x, touches[i].y, width / 8f, width / 8f);
   }

   image(img, 0, 0, width, height);
}