int cols;
float cell;
Stream[] streams;

String symbols = "的一是在不了有和人这中大为上个国我以要他时来用们生到作地于出就分对成会可主发年动同工也能下过子说产种面而方后多定行学法所民得经十三之进着等部度家电力里如水化高自二理起小物现实加量都两体制机当使点从业本去把性好应开它合还因由其些然前外天政四日那社义事平形相全表间样与关各重新线内数正心反你明看原又么利比或但质气第向道命此变条只没结解问意建月公无系军很情者最立代想已通";

void setup() {
   fullScreen(P2D);
   orientation(PORTRAIT);

   cell = width * 0.055;
   cols = int(width / cell) + 2;

   streams = new Stream[cols];

   textAlign(CENTER, CENTER);
   background(0);

   for (int i = 0; i < cols; i++) {
      streams[i] = new Stream(i * cell);
   }
}

void draw() {
   fill(0, 255 * 0.20);
   noStroke();
   rect(0, 0, width, height);

   for (int i = 0; i < streams.length; i++) {
      streams[i].update();
      streams[i].show();
   }
}

class Stream {
   float x;
   float y;
   float speed;
   int length;
   Drop[] drops;

   Stream(float x) {
      this.x = x;
      reset();
   }

   void reset() {
      y = random(-height, 0);
      speed = random(width * 0.008, width * 0.018);
      length = int(random(8, 24));

      drops = new Drop[length];

      for (int i = 0; i < drops.length; i++) {
         drops[i] = new Drop();
      }
   }

   void update() {
      y += speed;

      if (y - length * cell > height) {
         reset();
      }

      for (int i = 0; i < drops.length; i++) {
         drops[i].update();
      }
   }

   void show() {
      for (int i = 0; i < drops.length; i++) {
         float yy = y - i * cell;

         if (yy < -cell || yy > height + cell) {
            continue;
         }

         float alpha = map(i, 0, drops.length - 1, 255, 35);

         if (i == 0) {
            fill(190, 255, 190, 255);
         } else {
            fill(0, 255, 70, alpha);
         }

         textSize(cell * drops[i].size);
         text(drops[i].symbol, x, yy);
      }
   }
}

class Drop {
   String symbol;
   int changeDelay;
   int timer;
   float size;

   Drop() {
      changeSymbol();
      changeDelay = int(random(4, 18));
      timer = int(random(changeDelay));
      size = random(0.75, 1.15);
   }

   void update() {
      timer++;

      if (timer >= changeDelay) {
         timer = 0;
         changeSymbol();
      }
   }

   void changeSymbol() {
      int index = int(random(symbols.length()));
      symbol = str(symbols.charAt(index));
   }
}