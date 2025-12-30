package com.example.dominoes;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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

    private int leftX, leftY, rightX, rightY;
    private int dirL = -1, dirR = 1;  

    private int lastWidthL = 0, lastWidthR = 0;
    private int currentLeftX, currentRightX; // These track the exact "tips" of the snake


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
    
        setupJamaicanGame();
    }

    private void setupJamaicanGame() {
        myDeck = new Deck();
        allHands.clear();
        playerHandContainer.removeAllViews();
        boardContainer.removeAllViews();
        
        boardLeft = -1; boardRight = -1;
        dirL = -1; dirR = 1;
        consecutivePasses = 0;
        gameActive = true;
        lastPlayedTile = null;

        float den = getResources().getDisplayMetrics().density;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
    
        leftX = (screenW / 2) - (int)(30 * den); 
        rightX = leftX;
        leftY = (screenH / 2) - (int)(40 * den); 
        rightY = leftY;
    
        int startX = (screenW / 2) - (int)(27 * den); // Center of a horizontal tile
        currentLeftX = startX;
        currentRightX = startX;
        leftX = startX;
        rightX = startX;

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

                boardContainer.setOnTouchListener((vB, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        float touchX = event.getX();
                        boolean choseLeft = (touchX < vB.getWidth() / 2);

                        // Reset UI and play
                        isWaitingForSideChoice = false;
                        dv.setAlpha(1.0f);
                        boardContainer.setOnTouchListener(null);
                        playTile(d, dv, choseLeft, 0);
                    }
                    return true;
                });
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
    boolean isDbl = (d.getSide1() == d.getSide2());
    int matchVal = atLeft ? boardLeft : boardRight;

    // Identify which side of the domino touches the board
    int inner = (matchVal == -1) ? d.getSide1() : (d.getSide1() == matchVal ? d.getSide1() : d.getSide2());
    int outer = (inner == d.getSide1()) ? d.getSide2() : d.getSide1();

    float den = getResources().getDisplayMetrics().density;
    int screenW = getResources().getDisplayMetrics().widthPixels;

    // Determine orientation: Doubles are always vertical.
    // Otherwise, it's horizontal unless we are in a "wrap" row.
    boolean vert = isDbl;
    int w = vert ? (int) (30 * den) : (int) (54 * den);
    int h = vert ? (int) (54 * den) : (int) (30 * den);

    int finalX, finalY;

    if (boardLeft == -1) {
        // First Tile (Pose)
        finalX = (screenW / 2) - (w / 2);
        finalY = leftY; // Controlled by your "lower the table" variable
        currentLeftX = finalX;
        currentRightX = finalX + w;
    } else if (atLeft) {
        // --- LEFT SIDE LOGIC ---
        currentLeftX -= w; // Grow left by subtracting width
        finalX = currentLeftX;
        finalY = leftY;

        // Wrap Logic for Left
        if (finalX < (int) (20 * den)) {
            leftY += (int) (60 * den); // Move down
            currentLeftX = finalX + w; // Reset anchor for next tile
            // In a real wrap, we'd flip direction, but let's keep it simple first
        }
    } else {
        // --- RIGHT SIDE LOGIC ---
        finalX = currentRightX;
        finalY = rightY;
        currentRightX += w;

        // Wrap Logic for Right
        if (currentRightX > (screenW - (int) (80 * den))) {
            rightY += (int) (60 * den);
        }
    }

    // --- THE ORIENTATION FIX ---
    // v1 is the "first" half of the tile (top or left), v2 is the "second" (bottom
    // or right)
    int v1, v2;
    if (vert) {
        // Vertical: Inner touches the board.
        // If playing Left, inner is at the bottom. If playing Right, inner is at the
        // top.
        v1 = atLeft ? outer : inner;
        v2 = atLeft ? inner : outer;
    } else {
        // Horizontal:
        // If playing LEFT: [Outer | Inner] -> Inner touches the board on the right side
        // of the tile
        // If playing RIGHT: [Inner | Outer] -> Inner touches the board on the left side
        // of the tile
        v1 = atLeft ? outer : inner;
        v2 = atLeft ? inner : outer;
    }

    DominoView dv = new DominoView(this, v1, v2, vert, false);
    RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(w, h);
    p.leftMargin = finalX;
    p.topMargin = finalY;
    dv.setLayoutParams(p);

    // Highlight the move
    if (lastPlayedTile != null)
        lastPlayedTile.setBackgroundColor(Color.TRANSPARENT);
    dv.setBackgroundColor(Color.argb(40, 255, 255, 0));
    lastPlayedTile = dv;

    boardContainer.addView(dv);
}

    private void handleHumanPass() {
        if (!gameActive || currentPlayer != 0) return;
        consecutivePasses++;
        currentPlayer = 3;
        runComputerPlayers(3);
    }

    private boolean checkWin(int pIdx) {
        if (allHands.get(pIdx).isEmpty()) {
            gameActive = false;
            if (pIdx == 0 || pIdx == 2) teamHumanSets++; else teamOpponentSets++;
            turnIndicator.setText("WINNER: " + getPlayerName(pIdx));
            updateStatus(); return true;
        }
        return false;
    }

    private void resolveBlockedGame() {
        gameActive = false;
        int h = 0, o = 0;
        for (int i=0; i<4; i++) {
            int s = 0; for (Domino d : allHands.get(i)) s += (d.getSide1()+d.getSide2());
            if (i==0 || i==2) h+=s; else o+=s;
        }
        if (h < o) teamHumanSets++; else teamOpponentSets++;
        turnIndicator.setText("BLOCKED! " + (h < o ? "YOU WIN" : "OPPONENTS WIN"));
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
}