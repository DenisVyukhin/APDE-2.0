int cols;
int rows;
int cellSize;

int[][] grid;
int[][] nextGrid;

int sandColorA;
int sandColorB;
int sandColorC;

boolean eraseMode = false;


void setup() {
   fullScreen();
   orientation(PORTRAIT);

   cellSize = max(4, int(width * 0.012));

   cols = width / cellSize;
   rows = height / cellSize;

   grid = new int[cols][rows];
   nextGrid = new int[cols][rows];

   sandColorA = color(255, 205, 90);
   sandColorB = color(255, 165, 70);
   sandColorC = color(255, 235, 145);
}


void draw() {
   background(8, 10, 18);

   if (mousePressed) {
      paintSand(mouseX, mouseY);
   }

   updateSand();
   drawSand();
   drawUI();
}


void mousePressed() {
   if (mouseY < height * 0.14) {
      eraseMode = !eraseMode;
   } else {
      paintSand(mouseX, mouseY);
   }
}


void paintSand(float px, float py) {
   int cx = int(px / cellSize);
   int cy = int(py / cellSize);

   int radius = eraseMode ? 5 : 4;

   for (int x = -radius; x <= radius; x++) {
      for (int y = -radius; y <= radius; y++) {
         int gx = cx + x;
         int gy = cy + y;

         float d = sqrt(x * x + y * y);

         if (gx >= 0 && gx < cols && gy >= 0 && gy < rows && d <= radius) {
            if (eraseMode) {
               grid[gx][gy] = 0;
            } else if (random(1) < 0.75) {
               grid[gx][gy] = int(random(1, 4));
            }
         }
      }
   }
}


void updateSand() {
   clearNextGrid();

   for (int y = rows - 1; y >= 0; y--) {
      for (int x = 0; x < cols; x++) {
         int value = grid[x][y];

         if (value == 0) {
            continue;
         }

         int newX = x;
         int newY = y;

         if (canMove(x, y + 1)) {
            newY = y + 1;

         } else {
            int dir = random(1) < 0.5 ? -1 : 1;

            if (canMove(x + dir, y + 1)) {
               newX = x + dir;
               newY = y + 1;

            } else if (canMove(x - dir, y + 1)) {
               newX = x - dir;
               newY = y + 1;
            }
         }

         nextGrid[newX][newY] = value;
      }
   }

   int[][] temp = grid;
   grid = nextGrid;
   nextGrid = temp;
}


boolean canMove(int x, int y) {
   if (x < 0 || x >= cols || y < 0 || y >= rows) {
      return false;
   }

   return grid[x][y] == 0 && nextGrid[x][y] == 0;
}


void clearNextGrid() {
   for (int y = 0; y < rows; y++) {
      for (int x = 0; x < cols; x++) {
         nextGrid[x][y] = 0;
      }
   }
}


void drawSand() {
   noStroke();

   for (int y = 0; y < rows; y++) {
      for (int x = 0; x < cols; x++) {
         int value = grid[x][y];

         if (value == 0) {
            continue;
         }

         if (value == 1) {
            fill(sandColorA);
         } else if (value == 2) {
            fill(sandColorB);
         } else {
            fill(sandColorC);
         }

         rect(x * cellSize, y * cellSize, cellSize, cellSize);
      }
   }
}


void drawUI() {
   noStroke();

   fill(0, 0, 0, 90);
   rect(0, 0, width, height * 0.14);

   fill(255, 235);
   textAlign(LEFT, TOP);
   textSize(width * 0.045);
   text("Sand Simulator", width * 0.05, height * 0.035);

   fill(255, 150);
   textSize(width * 0.03);

   if (eraseMode) {
      text("Mode: Eraser | Tap top area to switch", width * 0.05, height * 0.085);
   } else {
      text("Mode: Sand | Tap top area to switch", width * 0.05, height * 0.085);
   }

   float bx = width * 0.78;
   float by = height * 0.045;
   float bw = width * 0.16;
   float bh = width * 0.07;

   fill(255, 255, 255, 22);
   rect(bx, by, bw, bh, bh * 0.5);

   fill(eraseMode ? color(255, 90, 90) : color(255, 205, 90));
   ellipse(bx + bh * 0.55, by + bh * 0.5, bh * 0.45, bh * 0.45);
}