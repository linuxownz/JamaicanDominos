package com.example.dominoes;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import android.view.MotionEvent;


public class MainActivity extends Activity {

    private Deck myDeck;
    private TextView countText, turnIndicator, matchScoreText;
    private LinearLayout playerHandContainer;
    private RelativeLayout boardContainer; 
    
    private int boardLeft = -1, boardRight = -1;
    private boolean gameActive = true;
    private int currentPlayer = 0; 
    private int consecutivePasses = 0;
    
    private boolean isWaitingForSideChoice = false;
    private Domino pendingDomino = null;
    private View pendingView = null;
    private View lastPlayedTile = null; // FIXED: Added missing variable

    private int teamHumanSets = 0, teamOpponentSets = 0;
    private SoundPool soundPool;
    private int smackId;
    private int soundClick;
    private int soundKnock;

    private int leftX, leftY, rightX, rightY;
    private int dirL = -1, dirR = 1;  

    private int lastWidthL = 0, lastWidthR = 0;
    private int currentLeftX, currentRightX; // These track the exact "tips" of the snake

    private ArrayList<Point> pathPoints = new ArrayList<>();
    private int leftPathIdx = 14; // Middle of the 30-point path
    private int rightPathIdx = 15; // Starting point for the first move to the right
    private ArrayList<Integer> pathDirections = new ArrayList<>();
    private ArrayList<ArrayList<Domino>> allHands = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_main);
    
        countText = findViewById(R.id.countText);
        turnIndicator = findViewById(R.id.turnIndicator);
        matchScoreText = findViewById(R.id.matchScoreText);
        playerHandContainer = findViewById(R.id.playerHandContainer);
        boardContainer = findViewById(R.id.boardContainer);
    
        AudioAttributes attrs = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        soundPool = new SoundPool.Builder().setMaxStreams(5).setAudioAttributes(attrs).build();
        smackId = soundPool.load(this, R.raw.smack, 1);
    
        findViewById(R.id.passButton).setOnClickListener(v -> handleHumanPass());
        findViewById(R.id.resetButton).setOnClickListener(v -> setupJamaicanGame());

        generatePath();
        setupJamaicanGame();
    }

    private void setupJamaicanGame() {
        myDeck = new Deck();
        allHands.clear();
        playerHandContainer.removeAllViews();
        boardContainer.removeAllViews();
        
        leftPathIdx = 20; 
        rightPathIdx = 21;

        boardLeft = -1; boardRight = -1;
        dirL = -1; dirR = 1;
        consecutivePasses = 0;
        gameActive = true;
        lastPlayedTile = null;

        for (int p = 0; p < 4; p++) {
            ArrayList<Domino> hand = new ArrayList<>();
            for (int i = 0; i < 7; i++) hand.add(myDeck.draw());
            allHands.add(hand);
        }
    
        int poser = -1; 
        Domino d6 = null;
        for (int p = 0; p < 4; p++) {
            for (Domino d : allHands.get(p)) {
                if (d.getSide1() == 6 && d.getSide2() == 6) { poser = p; d6 = d; break; }
            }
        }
    
        for (Domino d : allHands.get(0)) addDominoToHandUI(d);
        updateStatus();

        final int finalPoser = poser;
        final Domino finalD6 = d6;

        if (finalPoser == 0) {
            currentPlayer = 0;
            turnIndicator.setText("YOU HAVE THE 6-6! YOUR POSE.");
        } else {
            currentPlayer = finalPoser;
            turnIndicator.setText(getPlayerName(finalPoser) + " HAS THE 6-6.");

            // Now using the final copies inside the lambda
            new Handler().postDelayed(() -> {
                if (gameActive)
                    playTile(finalD6, null, true, finalPoser);
            }, 1500);
        }

    }

    private void addDominoToHandUI(final Domino d) {
        final DominoView dv = new DominoView(this, d.getSide1(), d.getSide2(), true, false);
        float den = getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams((int) (42 * den), (int) (72 * den));
        p.setMargins(8, 0, 8, 0);
        dv.setLayoutParams(p);

        dv.setOnClickListener(v -> {
            if (!gameActive || isWaitingForSideChoice || currentPlayer != 0)
                return;

            // 1. Pose Logic
            if (boardLeft == -1) {
                if (d.getSide1() == 6 && d.getSide2() == 6)
                    playTile(d, dv, true, 0);
                return;
            }

            // 2. Determine matches
            boolean mL = (d.getSide1() == boardLeft || d.getSide2() == boardLeft);
            boolean mR = (d.getSide1() == boardRight || d.getSide2() == boardRight);

            // 3. Choice Logic
            if (mL && mR && boardLeft != boardRight) {
                // THE TILE MATCHES BOTH SIDES - WAIT FOR INPUT
                isWaitingForSideChoice = true;
                pendingDomino = d;
                pendingView = dv;
                dv.setAlpha(0.5f);
                turnIndicator.setText("TAP LEFT OR RIGHT SIDE OF BOARD");


            } else if (mL) {
                playTile(d, dv, true, 0); // Only matches left
            } else if (mR) {
                playTile(d, dv, false, 0); // Only matches right
            } else {
                Toast.makeText(this, "No Match!", Toast.LENGTH_SHORT).show();
            }
        });
        playerHandContainer.addView(dv);
    }

    private void playTile(Domino d, View v, boolean isLeft, int pIdx) {
        if (soundPool != null) soundPool.play(smackId, 1, 1, 0, 0, 1);
        
        addTileToVisualBoard(d, isLeft);

        if (boardLeft == -1) { 
            boardLeft = d.getSide1(); boardRight = d.getSide2(); 
        } else if (isLeft) {
            boardLeft = (d.getSide1() == boardLeft) ? d.getSide2() : d.getSide1();
        } else {
            boardRight = (d.getSide1() == boardRight) ? d.getSide2() : d.getSide1();
        }

        allHands.get(pIdx).remove(d);
        if (v != null) playerHandContainer.removeView(v);
        
        consecutivePasses = 0;
        updateStatus();
        
        if (checkWin(pIdx)) return;

        currentPlayer = (pIdx + 3) % 4; // Move to next player in clockwise rotation
        
        if (gameActive) {
            if (currentPlayer == 0) {
                turnIndicator.setText("YOUR TURN");
            } else {
                runComputerPlayers(currentPlayer);
            }
        }
    }

    private void runComputerPlayers(final int pIdx) {
        if (!gameActive || pIdx == 0) return;

        turnIndicator.setText(getPlayerName(pIdx) + " is thinking...");
        
        // Staggered delay for readability
        int thinkTime = 1200 + (int)(Math.random() * 1000);

        new Handler().postDelayed(() -> {
            if (!gameActive) return;
            executeComputerMove(pIdx);
        }, thinkTime);
    }

   private void executeComputerMove(int pIdx) {
    if (!gameActive) return;

    ArrayList<Domino> hand = allHands.get(pIdx);
    Domino match = null;
    boolean playLeft = true;

    // Search for move
    for (Domino d : hand) {
        if (d.getSide1() == boardLeft || d.getSide2() == boardLeft) { match = d; playLeft = true; break; }
        if (d.getSide1() == boardRight || d.getSide2() == boardRight) { match = d; playLeft = false; break; }
    }

    if (match != null) {
        playTile(match, null, playLeft, pIdx);
    } else {
        // Player KNOCKS
        consecutivePasses++;
        turnIndicator.setText(getPlayerName(pIdx) + " KNOCKS!");
        
        if (consecutivePasses >= 4) {
            resolveBlockedGame();
        } else {
            // MOVE TO NEXT PLAYER IMMEDIATELY
            currentPlayer = (pIdx + 3) % 4; 
            new Handler().postDelayed(() -> {
                if (gameActive) runComputerPlayers(currentPlayer);
            }, 1200); // 1.2s delay so you can see the knock
        }
    }
} 

private void addTileToVisualBoard(Domino d, boolean atLeft) {
    int idx;
    if (boardLeft == -1) {
        idx = 20; 
    } else {
        idx = atLeft ? --leftPathIdx : rightPathIdx++;
    }

    if (idx < 0) idx = 0;
    if (idx >= pathPoints.size()) idx = pathPoints.size() - 1;

    Point p = pathPoints.get(idx);
    int currentDir = pathDirections.get(idx); 
    float den = getResources().getDisplayMetrics().density;

    boolean isDbl = (d.getSide1() == d.getSide2());
    
    // 1. Calculate Pip Orientation
    int matchVal = atLeft ? boardLeft : boardRight;
    int inner = (matchVal == -1) ? d.getSide1() : (d.getSide1() == matchVal ? d.getSide1() : d.getSide2());
    int outer = (inner == d.getSide1()) ? d.getSide2() : d.getSide1();

    int v1, v2;
    if (!atLeft) { // RIGHT SIDE
        if (currentDir == 1) { v1 = inner; v2 = outer; }
        else { v1 = outer; v2 = inner; }
    } else { // LEFT SIDE
        // If currentDir is -1 (Left), the 'inner' pip must be on the right (v2)
        if (currentDir == -1) { v1 = outer; v2 = inner; }
        else { v1 = inner; v2 = outer; }
    }

    // 2. Create the View (Named 'dv' so the rest of the code works)
    DominoView dv = new DominoView(this, v1, v2, isDbl, false);

    // 3. Dimensions
    int longSide = (int) (48 * den);
    int shortSide = (int) (30 * den);
    
    int w = isDbl ? shortSide : longSide;
    int h = isDbl ? longSide : shortSide;

    // 4. Uniform Spacing (Center tile in a 62dp cell)
    int cellSize = (int) (50 * den);
    int offsetX = (cellSize - w) / 2;
    int offsetY = (cellSize - h) / 2;

    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(w, h);
    params.leftMargin = p.x + offsetX;
    params.topMargin = p.y + offsetY;
    dv.setLayoutParams(params);

    // 5. Visual Highlight and Add to Board
    if (lastPlayedTile != null) lastPlayedTile.setBackgroundColor(Color.TRANSPARENT);
    dv.setBackgroundColor(Color.argb(50, 255, 255, 0));
    lastPlayedTile = dv;
    
    boardContainer.addView(dv);

    final int thisTileIdx = idx; // Capture the current index

    dv.setOnClickListener(v -> {
        if (isWaitingForSideChoice && pendingDomino != null) {
            // If the user taps the tile at the left end
            if (thisTileIdx == leftPathIdx) {
                finishSideChoice(true);
            }
            // If the user taps the tile at the right end
            else if (thisTileIdx == rightPathIdx - 1) {
                finishSideChoice(false);
            }
        }
    });
}

private void handleHumanPass() {
    if (!gameActive || currentPlayer != 0 || isWaitingForSideChoice) return;

    // 1. Check if the player ACTUALLY has a move
    boolean hasMove = false;
    ArrayList<Domino> hand = allHands.get(0);
    
    for (Domino d : hand) {
        if (d.getSide1() == boardLeft || d.getSide2() == boardLeft ||
            d.getSide1() == boardRight || d.getSide2() == boardRight) {
            hasMove = true;
            break;
        }
    }

    // 2. Only allow the pass if they have no moves
    if (hasMove) {
        Toast.makeText(this, "You have a move! Play your tile.", Toast.LENGTH_SHORT).show();
    } else {
        // Valid Knock
        consecutivePasses++;
        if (soundPool != null) soundPool.play(smackId, 1, 1, 0, 0, 0.5f); // Soft smack for knock
        
        turnIndicator.setText("YOU KNOCK!");
        
        if (consecutivePasses >= 4) {
            resolveBlockedGame();
        } else {
            currentPlayer = 1; // Move to Player 1 (Computer)
            new Handler().postDelayed(() -> {
                if (gameActive) runComputerPlayers(currentPlayer);
            }, 1000);
        }
    }
}

    private boolean checkWin(int pIdx) {
        if (allHands.get(pIdx).isEmpty()) {
            gameActive = false;
            if (pIdx == 0 || pIdx == 2)
                teamHumanSets++;
            else
                teamOpponentSets++;

            turnIndicator.setText("WINNER: " + getPlayerName(pIdx));
            updateStatus();

            // Wait 2 seconds so player sees the win, then fly tiles away
            new Handler().postDelayed(() -> {
                clearBoardWithAnimation(() -> setupJamaicanGame());
            }, 2000);

            return true;
        }
        return false;
    }

    private void resolveBlockedGame() {
        gameActive = false;
        int h = 0, o = 0;
        
        // Calculate totals for both teams
        for (int i = 0; i < 4; i++) {
            int s = 0; 
            for (Domino d : allHands.get(i)) s += (d.getSide1() + d.getSide2());
            if (i == 0 || i == 2) h += s; else o += s;
        }
    
        if (h < o) {
            teamHumanSets++;
            turnIndicator.setText("BLOCKED! YOU WIN (" + h + " vs " + o + ")");
        } else if (o < h) {
            teamOpponentSets++;
            turnIndicator.setText("BLOCKED! OPPONENTS WIN (" + o + " vs " + h + ")");
        } else {
            turnIndicator.setText("BLOCKED! IT'S A TIE!");
        }
    
        updateStatus();
    
        // MISSING PIECE: Wait 3 seconds then reset, exactly like checkWin
        new Handler().postDelayed(() -> {
            clearBoardWithAnimation(() -> setupJamaicanGame());
        }, 3000);
    }

    private void updateStatus() {
        countText.setText("P1: "+allHands.get(1).size()+" | Partner: "+allHands.get(2).size()+" | P3: "+allHands.get(3).size());
        matchScoreText.setText("SETS - You: " + teamHumanSets + " | Opp: " + teamOpponentSets);
    }

    private String getPlayerName(int i) {
        String[] n = {"You", "Player 1", "Partner", "Player 3"};
        return n[i];
    }

    class DominoView extends View {
        private int s1, s2;
        private boolean isVertical, isHidden;
        private Paint pnt = new Paint(Paint.ANTI_ALIAS_FLAG);

        public DominoView(Context context, int s1, int s2, boolean isVertical, boolean isHidden) {
            super(context);
            this.s1 = s1; this.s2 = s2; this.isVertical = isVertical; this.isHidden = isHidden;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            RectF r = new RectF(2, 2, w - 2, h - 2);
            pnt.setStyle(Paint.Style.FILL);
            pnt.setColor(isHidden ? Color.parseColor("#D2B48C") : Color.WHITE);
            canvas.drawRoundRect(r, 8, 8, pnt);
            pnt.setStyle(Paint.Style.STROKE);
            pnt.setColor(Color.BLACK);
            canvas.drawRoundRect(r, 8, 8, pnt);
            if (!isHidden) {
                if (isVertical) canvas.drawLine(0, h/2, w, h/2, pnt);
                else canvas.drawLine(w/2, 0, w/2, h, pnt);
                pnt.setStyle(Paint.Style.FILL);
                float rad = Math.min(w, h) * 0.08f;
                if (isVertical) {
                    drawPips(canvas, s1, new RectF(0, 0, w, h/2), rad);
                    drawPips(canvas, s2, new RectF(0, h/2, w, h), rad);
                } else {
                    drawPips(canvas, s1, new RectF(0, 0, w/2, h), rad);
                    drawPips(canvas, s2, new RectF(w/2, 0, w, h), rad);
                }
            }
        }

        private void drawPips(Canvas canvas, int count, RectF area, float r) {
            float cx = area.centerX(), cy = area.centerY();
            float l = area.left + area.width() * 0.25f, rt = area.left + area.width() * 0.75f;
            float t = area.top + area.height() * 0.25f, b = area.top + area.height() * 0.75f;
            if (count % 2 != 0) canvas.drawCircle(cx, cy, r, pnt);
            if (count >= 2) { canvas.drawCircle(l, t, r, pnt); canvas.drawCircle(rt, b, r, pnt); }
            if (count >= 4) { canvas.drawCircle(rt, t, r, pnt); canvas.drawCircle(l, b, r, pnt); }
            if (count == 6) { canvas.drawCircle(l, cy, r, pnt); canvas.drawCircle(rt, cy, r, pnt); }
        }
    }

    private void clearBoardWithAnimation(final Runnable onComplete) {
        int childCount = boardContainer.getChildCount();
        if (childCount == 0) {
            onComplete.run();
            return;
        }

        for (int i = 0; i < childCount; i++) {
            View tile = boardContainer.getChildAt(i);
            // Random "fly out" coordinates
            float destX = (Math.random() > 0.5 ? 1 : -1) * 2000;
            float destY = (Math.random() > 0.5 ? 1 : -1) * 2000;
            float rotation = (float) (Math.random() * 720);

            tile.animate()
                    .translationX(destX)
                    .translationY(destY)
                    .rotation(rotation)
                    .setDuration(800)
                    .setStartDelay(i * 20) // Staggered "waterfall" effect
                    .withEndAction(i == childCount - 1 ? onComplete : null)
                    .start();
        }
    }

    private void generatePath() {
        pathPoints.clear();
        pathDirections.clear();
        // Pre-fill to avoid index out of bounds
        for(int i=0; i<60; i++) {
            pathPoints.add(new Point(0,0));
            pathDirections.add(1);
        }
    
        float den = getResources().getDisplayMetrics().density;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
    
        int cellSize = (int) (50 * den);
        int stepY = (int) (65 * den);
        int centerX = (screenW / 2) - (cellSize / 2);
        int centerY = (screenH / 2) - (cellSize / 2);
    
        // --- 1. THE CENTER (POSE) ---
        pathPoints.set(20, new Point(centerX, centerY));
        pathDirections.set(20, 1);
    
        // --- 2. GENERATE RIGHT SIDE (21 to 59) ---
        int x = centerX + cellSize;
        int y = centerY;
        int dir = 1; // Moving Right
        for (int i = 21; i < 60; i++) {
            pathPoints.set(i, new Point(x, y));
            pathDirections.set(i, dir);
            
            int nextX = x + (cellSize * dir);
            if (nextX > screenW - (int) (70 * den) || nextX < (int) (30 * den)) {
                dir *= -1;
                y += stepY; // Move DOWN on the right
            } else {
                x = nextX;
            }
        }
    
        // --- 3. GENERATE LEFT SIDE (19 down to 0) ---
        x = centerX - cellSize;
        y = centerY;
        dir = -1; // Moving Left
        for (int i = 19; i >= 0; i--) {
            pathPoints.set(i, new Point(x, y));
            pathDirections.set(i, dir);
    
            int nextX = x + (cellSize * dir);
            if (nextX > screenW - (int) (70 * den) || nextX < (int) (30 * den)) {
                dir *= -1;
                y -= stepY; // Move UP on the left
            } else {
                x = nextX;
            }
        }
    }

    private void finishSideChoice(boolean choseLeft) {
        isWaitingForSideChoice = false;
        if (pendingView != null)
            pendingView.setAlpha(1.0f);

        Domino d = pendingDomino;
        View v = pendingView;

        pendingDomino = null;
        pendingView = null;

        turnIndicator.setText("YOUR TURN");
        playTile(d, v, choseLeft, 0);
    }
}