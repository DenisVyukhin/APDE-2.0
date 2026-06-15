PGraphics img;

void setup() {
   fullScreen(P2D);
   img = createGraphics(int(width/25), int(height/25));
   
   img.beginDraw();
   img.background(0);
   img.endDraw();
}

void draw() {
   background(0);
   
   img.beginDraw();
   img.noStroke();
   img.fill(0, 150, 255);
   img.ellipse(mouseX/25f, mouseY/25f, img.width/6f, img.width/6f);
   img.endDraw();
   
   img.filter(BLUR, 1);
   
   blendMode(ADD);
   image(img, 0, 0, width, height);
   image(img, 0, 0, width, height);
}