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
import android.widget.Button;

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

    private int roundPoser = -1;
    private int lastWinner = -1;

    private RelativeLayout mainRoot; // Add mainRoot here

    private Boolean isAnimating = false;
    private android.widget.Button resetButton = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_main);

        mainRoot = findViewById(R.id.mainRoot);

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

        resetButton = findViewById(R.id.resetButton);
        resetButton.setOnClickListener(v -> {
            if (!gameActive) {
                // SCENARIO A: Round is over. Act as "Next Round".
                // Match scores (teamHumanSets, teamOpponentSets) are kept.
                clearBoardWithAnimation(() -> setupJamaicanGame());
            } else {
                // SCENARIO B: Game is active. Act as "Hard Reset".
                teamHumanSets = 0;
                teamOpponentSets = 0;
                lastWinner = -1; // Reset poser logic to 6-6
                clearBoardWithAnimation(() -> setupJamaicanGame());
            }
        });

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
        boardLeft = -1;
        boardRight = -1;
        gameActive = true;
        lastPlayedTile = null;

        // Deal hands
        for (int p = 0; p < 4; p++) {
            ArrayList<Domino> hand = new ArrayList<>();
            for (int i = 0; i < 7; i++)
                hand.add(myDeck.draw());
            allHands.add(hand);
        }

        sortHumanHand();

        int poser = -1;
        Domino poseTile = null;

        if (lastWinner == -1) {
            // FIRST GAME: Find who has 6-6
            for (int p = 0; p < 4; p++) {
                for (Domino d : allHands.get(p)) {
                    if (d.getSide1() == 6 && d.getSide2() == 6) {
                        poser = p;
                        poseTile = d;
                        break;
                    }
                }
            }
        } else {
            // SUBSEQUENT GAMES: The winner of the last round poses ANY tile they want
            poser = lastWinner;
        }

        // UI Updates
        // for (Domino d : allHands.get(0))
        // addDominoToHandUI(d);

        updateStatus();

        roundPoser = poser;
        currentPlayer = poser;
        if (poser == 0) {
            turnIndicator.setText(lastWinner == -1 ? "YOU HAVE 6-6! YOUR POSE." : "YOU WON! CHOOSE ANY TILE TO POSE.");
        } else {
            turnIndicator.setText(getPlayerName(poser) + " IS POSING...");

            // If computer is posing, they just play their first tile
            final int finalPoser = poser;
            final Domino finalPoseTile = poseTile;
            new Handler().postDelayed(() -> {
                if (gameActive) {
                    // If it's the first game, they MUST play the 6-6
                    // If it's not the first game, they play their first available tile
                    Domino dToPlay = (finalPoseTile != null) ? finalPoseTile : allHands.get(finalPoser).get(0);
                    playTile(dToPlay, null, true, finalPoser);
                }
            }, 1500);
        }

        updateStatus();
        for (int i = 0; i < 4; i++) {
            updatePlayerHandDisplay(i, false);
        }

        resetButton.setText("Reset Match");
    }

    private void addDominoToHandUI(final Domino d) {
        final DominoView dv = new DominoView(this, d.getSide1(), d.getSide2(), true, false);
        float den = getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams((int) (42 * den), (int) (72 * den));
        p.setMargins(8, 0, 8, 0);
        dv.setLayoutParams(p);

        dv.setOnClickListener(v -> {
            if (isAnimating || !gameActive || isWaitingForSideChoice || currentPlayer != 0)
                return;

            // 1. Pose Logic
            if (boardLeft == -1) {
                if (lastWinner == -1) {
                    // FIRST GAME: Must be 6-6
                    if (d.getSide1() == 6 && d.getSide2() == 6) {
                        playTile(d, dv, true, 0);
                    } else {
                        Toast.makeText(this, "First game must pose with 6-6!", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // SUBSEQUENT GAMES: Winner poses anything
                    playTile(d, dv, true, 0);
                }
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
                turnIndicator.setText("TAP LEFT OR RIGHT TILE ON THE BOARD");

                highlightEndTiles(true);

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
        isAnimating = true;
        int targetIdx;
        if (boardLeft == -1) {
            targetIdx = 20; // Center Pose
        } else {
            // We peek at the next available index
            targetIdx = isLeft ? leftPathIdx - 1 : rightPathIdx;
        }

        // Start animation using this specific index
        animateTileToBoard(d, v, targetIdx, pIdx, isLeft);

        // UI Cleanup
        allHands.get(pIdx).remove(d);

        if (pIdx == 0) {
            sortHumanHand();
        } else {
            // Only refresh computer hand displays
            updatePlayerHandDisplay(pIdx, false);
        }

        if (v != null && pIdx == 0)
            playerHandContainer.removeView(v);
        // updatePlayerHandDisplay(pIdx, false);
    }

    // New method to finish the logic after animation
    private void completePlayTile(Domino d, boolean isLeft, int pIdx, int targetIdx) {
        if (soundPool != null)
            soundPool.play(smackId, 1, 1, 0, 0, 1);

        // Pass the index directly to the visual drawer
        addTileToVisualBoard(d, isLeft, targetIdx);

        if (boardLeft == -1) {
            boardLeft = d.getSide1();
            boardRight = d.getSide2();
            // No index change needed for pose
        } else if (isLeft) {
            boardLeft = (d.getSide1() == boardLeft) ? d.getSide2() : d.getSide1();
            leftPathIdx = targetIdx; // Update global tracker to the used index
        } else {
            boardRight = (d.getSide1() == boardRight) ? d.getSide2() : d.getSide1();
            rightPathIdx = targetIdx + 1; // Move right boundary up
        }

        isAnimating = false;
        consecutivePasses = 0;
        updateStatus();
        if (checkWin(pIdx))
            return;

        currentPlayer = (pIdx + 1) % 4;
        updateStatus();
        if (gameActive) {
            if (currentPlayer == 0)
                turnIndicator.setText("YOUR TURN");
            else
                runComputerPlayers(currentPlayer);
        }
    }

    private void runComputerPlayers(final int pIdx) {
        if (!gameActive || pIdx == 0)
            return;

        turnIndicator.setText(getPlayerName(pIdx) + " is thinking...");

        // Staggered delay for readability
        int thinkTime = 1200 + (int) (Math.random() * 1000);

        new Handler().postDelayed(() -> {
            if (!gameActive)
                return;
            executeComputerMove(pIdx);
        }, thinkTime);
    }

    private void executeComputerMove(int pIdx) {
        if (!gameActive)
            return;

        ArrayList<Domino> hand = allHands.get(pIdx);
        Domino match = null;
        boolean playLeft = true;

        // Search for move
        for (Domino d : hand) {
            if (d.getSide1() == boardLeft || d.getSide2() == boardLeft) {
                match = d;
                playLeft = true;
                break;
            }
            if (d.getSide1() == boardRight || d.getSide2() == boardRight) {
                match = d;
                playLeft = false;
                break;
            }
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
                currentPlayer = (pIdx + 1) % 4;
                updateStatus();
                new Handler().postDelayed(() -> {
                    if (gameActive)
                        runComputerPlayers(currentPlayer);
                }, 1200); // 1.2s delay so you can see the knock
            }
        }
    }

    private void addTileToVisualBoard(Domino d, boolean atLeft, int idx) {
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
            if (currentDir == 1) {
                v1 = inner;
                v2 = outer;
            } else {
                v1 = outer;
                v2 = inner;
            }
        } else { // LEFT SIDE
            // If currentDir is -1 (Left), the 'inner' pip must be on the right (v2)
            if (currentDir == -1) {
                v1 = outer;
                v2 = inner;
            } else {
                v1 = inner;
                v2 = outer;
            }
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
        if (lastPlayedTile != null)
            lastPlayedTile.setBackgroundColor(Color.TRANSPARENT);
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
        if (!gameActive || currentPlayer != 0 || isWaitingForSideChoice)
            return;

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
            if (soundPool != null)
                soundPool.play(smackId, 1, 1, 0, 0, 0.5f); // Soft smack for knock

            turnIndicator.setText("YOU KNOCK!");

            if (consecutivePasses >= 4) {
                resolveBlockedGame();
            } else {
                currentPlayer = (0 + 1) % 4; // Move to Player 1 (Computer)
                updateStatus();
                new Handler().postDelayed(() -> {
                    if (gameActive)
                        runComputerPlayers(currentPlayer);
                }, 1000);
            }
        }
    }

    private boolean checkWin(int pIdx) {
        if (allHands.get(pIdx).isEmpty()) {
            lastWinner = pIdx;
            gameActive = false;

            if (pIdx == 0 || pIdx == 2)
                teamHumanSets++;
            else
                teamOpponentSets++;

            updateStatus();

            resetButton.setText("Next round");

            // FIX: If match is over, stop here and do NOT run the Handler
            if (checkMatchOver()) {
                return true;
            }

            turnIndicator.setText("WINNER: " + getPlayerName(pIdx));

            return true;
        }
        return false;
    }

    private void resolveBlockedGame() {
        gameActive = false;
        int h = 0, o = 0;

        for (int i = 0; i < 4; i++) {
            int s = 0;
            for (Domino d : allHands.get(i))
                s += (d.getSide1() + d.getSide2());
            if (i == 0 || i == 2)
                h += s;
            else
                o += s;
        }

        if (h < o) {
            lastWinner = 0; // Human team wins
            teamHumanSets++;
            turnIndicator.setText("BLOCKED! YOU WIN (" + h + " vs " + o + ")");
        } else if (o < h) {
            lastWinner = 1; // Opponent team wins (Player 1)
            teamOpponentSets++;
            turnIndicator.setText("BLOCKED! OPPONENTS WIN (" + o + " vs " + h + ")");
        } else {
            // TIE BREAKER: The team that DID NOT pose wins the tie
            if (roundPoser == 0 || roundPoser == 2) {
                lastWinner = 1;
                teamOpponentSets++;
                turnIndicator.setText("TIE! OPPONENTS WIN (You Posed)");
            } else {
                lastWinner = 0;
                teamHumanSets++;
                turnIndicator.setText("TIE! YOU WIN (They Posed)");
            }
        }

        updateStatus();
        revealAllHands();

        resetButton.setText("Next Round");

        if (checkMatchOver()) {
            return;
        }
    }

    private void updateStatus() {
        matchScoreText.setText("SETS - You: " + teamHumanSets + " | Opp: " + teamOpponentSets);

        boolean reveal = !gameActive;
        for (int i = 1; i <= 3; i++) {
            updatePlayerHandDisplay(i, reveal);
        }

        // This ensures your hand UI stays in sync with game states (like when
        // gameActive changes)
        if (gameActive) {
            // Optional: you could call sortHumanHand() here if you change your UI
            // based on turn highlights, but the logic above is usually enough.
        }
    }

    private String getPlayerName(int i) {
        String[] n = { "You", "Player 1", "Partner", "Player 3" };
        return n[i];
    }

    class DominoView extends View {
        private int s1, s2;
        private boolean isVertical, isHidden;
        private Paint pnt = new Paint(Paint.ANTI_ALIAS_FLAG);

        public DominoView(Context context, int s1, int s2, boolean isVertical, boolean isHidden) {
            super(context);
            this.s1 = s1;
            this.s2 = s2;
            this.isVertical = isVertical;
            this.isHidden = isHidden;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            RectF r = new RectF(2, 2, w - 2, h - 2);

            // 1. Draw Background
            pnt.setStyle(Paint.Style.FILL);
            // If hidden: Dark Green (Jamaican theme), If revealed: White
            pnt.setColor(isHidden ? Color.parseColor("#2E7D32") : Color.WHITE);
            canvas.drawRoundRect(r, 8, 8, pnt);

            // 2. Draw Outline/Border
            pnt.setStyle(Paint.Style.STROKE);
            pnt.setStrokeWidth(3);
            // Border should be dark or black so it's visible on white
            pnt.setColor(isHidden ? Color.parseColor("#1B5E20") : Color.BLACK);
            canvas.drawRoundRect(r, 8, 8, pnt);

            if (!isHidden) {
                // 3. Draw Center Dividing Line
                if (isVertical)
                    canvas.drawLine(0, h / 2, w, h / 2, pnt);
                else
                    canvas.drawLine(w / 2, 0, w / 2, h, pnt);

                // 4. Draw Pips (Always black)
                pnt.setStyle(Paint.Style.FILL);
                pnt.setColor(Color.BLACK);
                float rad = Math.min(w, h) * 0.08f;
                if (isVertical) {
                    drawPips(canvas, s1, new RectF(0, 0, w, h / 2), rad);
                    drawPips(canvas, s2, new RectF(0, h / 2, w, h), rad);
                } else {
                    drawPips(canvas, s1, new RectF(0, 0, w / 2, h), rad);
                    drawPips(canvas, s2, new RectF(w / 2, 0, w, h), rad);
                }
            }
        }

        private void drawPips(Canvas canvas, int count, RectF area, float r) {
            float cx = area.centerX(), cy = area.centerY();
            float l = area.left + area.width() * 0.25f, rt = area.left + area.width() * 0.75f;
            float t = area.top + area.height() * 0.25f, b = area.top + area.height() * 0.75f;
            if (count % 2 != 0)
                canvas.drawCircle(cx, cy, r, pnt);
            if (count >= 2) {
                canvas.drawCircle(l, t, r, pnt);
                canvas.drawCircle(rt, b, r, pnt);
            }
            if (count >= 4) {
                canvas.drawCircle(rt, t, r, pnt);
                canvas.drawCircle(l, b, r, pnt);
            }
            if (count == 6) {
                canvas.drawCircle(l, cy, r, pnt);
                canvas.drawCircle(rt, cy, r, pnt);
            }
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
        for (int i = 0; i < 60; i++) {
            pathPoints.add(new Point(0, 0));
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

    private void highlightEndTiles(boolean show) {
        for (int i = 0; i < boardContainer.getChildCount(); i++) {
            View child = boardContainer.getChildAt(i);
            // Reset all tiles to transparent first
            child.setBackgroundColor(Color.TRANSPARENT);

            if (show) {
                // Check if this specific view is one of the ends
                // We can check the LayoutParams to match the coordinates
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) child.getLayoutParams();
                Point pL = pathPoints.get(leftPathIdx);
                Point pR = pathPoints.get(rightPathIdx - 1);

                // If the tile's position matches an end point, highlight it
                if ((lp.leftMargin >= pL.x && lp.leftMargin <= pL.x + 50) ||
                        (lp.leftMargin >= pR.x && lp.leftMargin <= pR.x + 50)) {
                    child.setBackgroundColor(Color.argb(150, 255, 255, 0)); // Semi-transparent yellow
                }
            }
        }
    }

    private boolean checkMatchOver() {
        if (teamHumanSets >= 6 || teamOpponentSets >= 6) {
            gameActive = false;

            revealAllHands();// Reveal everyone's remaining tiles for the final tally

            String result;

            // Custom Jamaican Six-Love Messaging
            if (teamHumanSets == 6 && teamOpponentSets == 0) {
                result = "SIX-LOVE! YOU GAVE THEM A WASH!";
                turnIndicator.setTextColor(Color.parseColor("#4CAF50")); // Winner Green
            } else if (teamOpponentSets == 6 && teamHumanSets == 0) {
                result = "SIX-LOVE! YOU GOT WASHED!";
                turnIndicator.setTextColor(Color.parseColor("#F44336")); // Loser Red
            } else if (teamHumanSets >= 6) {
                result = "MATCH OVER! YOU WIN " + teamHumanSets + "-" + teamOpponentSets;
                turnIndicator.setTextColor(Color.YELLOW);
            } else {
                result = "MATCH OVER! OPPONENTS WIN " + teamOpponentSets + "-" + teamHumanSets;
                turnIndicator.setTextColor(Color.YELLOW);
            }

            // Make the text pop since it's now in the corner
            turnIndicator.setText(result);
            turnIndicator.setTextSize(20f);

            // Disable the board and hands
            boardContainer.setAlpha(0.4f);
            playerHandContainer.setEnabled(false);
            playerHandContainer.setAlpha(0.5f);

            return true;
        }
        return false;
    }

    private void revealAllHands() {
        for (int i = 1; i <= 3; i++) {
            updatePlayerHandDisplay(i, true);
        }
    }

    private void updatePlayerHandDisplay(int pIdx, boolean reveal) {
        if (pIdx == 0)
            return;

        String tag = "hand_container_" + pIdx;
        LinearLayout container = boardContainer.findViewWithTag(tag);

        if (container == null) {
            container = new LinearLayout(this);
            container.setTag(tag);
            boardContainer.addView(container);
        }

        if (pIdx == currentPlayer && gameActive) {
            container.setBackgroundColor(Color.argb(120, 255, 255, 255)); // Subtle white glow
        } else {
            container.setBackgroundColor(Color.TRANSPARENT);
        }
        container.removeAllViews();
        float den = getResources().getDisplayMetrics().density;
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(-2, -2);

        if (pIdx == 0) { // BOTTOM (You)
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            params.addRule(RelativeLayout.CENTER_HORIZONTAL);
            params.bottomMargin = (int) (100 * den); // High enough to stay above playerHandContainer
        } else if (pIdx == 1) { // LEFT (P1)
            params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            params.addRule(RelativeLayout.CENTER_VERTICAL);
            params.leftMargin = (int) (10 * den);
            params.topMargin = (int) (60 * den); // Offset to clear the Score Text
            container.setOrientation(LinearLayout.VERTICAL);
        } else if (pIdx == 2) { // TOP (Partner)
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            params.addRule(RelativeLayout.CENTER_HORIZONTAL);
            params.topMargin = (int) (10 * den);
        } else if (pIdx == 3) { // RIGHT (P3)
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            params.addRule(RelativeLayout.CENTER_VERTICAL);
            params.rightMargin = (int) (10 * den);
            params.topMargin = (int) (60 * den); // Offset to clear the Turn Indicator
            container.setOrientation(LinearLayout.VERTICAL);
        }

        container.setLayoutParams(params);

        for (Domino d : allHands.get(pIdx)) {
            boolean vertical = (pIdx == 0 || pIdx == 2);
            DominoView dv = new DominoView(this, d.getSide1(), d.getSide2(), vertical, !reveal);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    (int) ((vertical ? 24 : 40) * den),
                    (int) ((vertical ? 40 : 24) * den));
            lp.setMargins(2, 2, 2, 2);
            dv.setLayoutParams(lp);
            container.addView(dv);
        }
    }

    private void animateTileToBoard(Domino d, View sourceView, int targetIdx, int pIdx, boolean isLeft) {
        float den = getResources().getDisplayMetrics().density;

        // 1. Determine orientation for the ghost
        boolean isDbl = (d.getSide1() == d.getSide2());
        int w = isDbl ? (int) (30 * den) : (int) (48 * den);
        int h = isDbl ? (int) (48 * den) : (int) (30 * den);
        int cellSize = (int) (50 * den);

        // Create ghost (isHidden = false so pips are visible during flight)
        final DominoView ghost = new DominoView(this, d.getSide1(), d.getSide2(), isDbl, false);

        // 2. Get Start Position
        int[] startLoc = new int[2];
        if (sourceView != null) {
            sourceView.getLocationInWindow(startLoc);
        } else {
            View container = boardContainer.findViewWithTag("hand_container_" + pIdx);
            if (container != null)
                container.getLocationInWindow(startLoc);
            else
                boardContainer.getLocationInWindow(startLoc);
        }

        // 3. Get End Position + Centering Offset
        Point p = pathPoints.get(targetIdx);
        int offsetX = (cellSize - w) / 2;
        int offsetY = (cellSize - h) / 2;

        float destX = p.x + offsetX + boardContainer.getX();
        float destY = p.y + offsetY + boardContainer.getY();

        // 4. Setup and Add Ghost
        ghost.setLayoutParams(new RelativeLayout.LayoutParams(w, h));
        mainRoot.addView(ghost);

        // We use setX/Y to position it in the root coordinate system
        ghost.setX(startLoc[0]);
        ghost.setY(startLoc[1]);

        // 5. Animate to destination
        ghost.animate()
                .x(destX)
                .y(destY)
                .setDuration(450)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> {
                    mainRoot.removeView(ghost);
                    // FIXED: Passing targetIdx here resolves the compiler error
                    completePlayTile(d, isLeft, pIdx, targetIdx);
                }).start();
    }

    private void sortHumanHand() {
        if (allHands == null || allHands.isEmpty())
            return;
        ArrayList<Domino> hand = allHands.get(0);
        // Sort by highest pip value first, then by the smaller pip value
        java.util.Collections.sort(hand, (d1, d2) -> {
            int sum1 = d1.getSide1() + d1.getSide2();
            int sum2 = d2.getSide1() + d2.getSide2();
            
            if (sum1 != sum2) {
                return Integer.compare(sum1, sum2);
            }
            // If sums are equal (e.g., 5-1 vs 4-2), sort by the higher individual pip
            return Integer.compare(Math.max(d1.getSide1(), d1.getSide2()), 
                                   Math.max(d2.getSide1(), d2.getSide2()));
        });

        // Rebuild the UI container for the human player
        playerHandContainer.removeAllViews();
        for (Domino d : hand) {
            addDominoToHandUI(d);
        }
    }
}