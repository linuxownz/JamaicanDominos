package com.example.dominoes;

import android.os.Bundle;
import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private Deck myDeck;
    private TextView countText;
    private TextView display;
    private LinearLayout playerHandContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        display = (TextView) findViewById(R.id.dominoDisplay);
        countText = (TextView) findViewById(R.id.countText);
        playerHandContainer = (LinearLayout) findViewById(R.id.playerHandContainer);
        Button dealBtn = (Button) findViewById(R.id.dealButton);
        Button resetBtn = (Button) findViewById(R.id.resetButton);

        try {
            myDeck = new Deck();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        dealBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Domino d = myDeck.draw();
                if (d != null) {
                    // 1. Update the main focus display
                    display.setText(d.toString());
                    countText.setText("Tiles in Deck: " + myDeck.size());

                    // 2. Add the domino to the scrolling hand
                    addDominoToHand(d);
                } else {
                    Toast.makeText(MainActivity.this, "No more tiles!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                myDeck = new Deck();
                display.setText("?");
                countText.setText("Tiles in Deck: 28");
                playerHandContainer.removeAllViews(); // Clear the hand
            }
        });
    }

    private void addDominoToHand(Domino d) {
        TextView tileView = new TextView(this);
        tileView.setText(d.toString());
        tileView.setGravity(Gravity.CENTER);
        tileView.setBackgroundResource(R.drawable.domino_tile);
        tileView.setTextColor(android.graphics.Color.BLACK);
        tileView.setTextSize(20); // Slightly larger text
        tileView.setTypeface(null, android.graphics.Typeface.BOLD);

        // ADD PADDING: This keeps the text from touching the black border
        // (left, top, right, bottom) in pixels
        tileView.setPadding(0, 10, 0, 10); 

        // INCREASE SIZE: 140 width, 220 height (adjust 220 higher if still cut off)
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(140, 220);
        params.setMargins(15, 10, 15, 10);
        tileView.setLayoutParams(params);

        playerHandContainer.addView(tileView);
    }
}