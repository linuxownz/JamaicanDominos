package com.example.dominoes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deck {
    private List<Domino> tiles;

    public Deck() {
        tiles = new ArrayList<>();
        // Create 28 Jamaican dominoes (0-0 to 6-6)
        for (int i = 0; i <= 6; i++) {
            for (int j = i; j <= 6; j++) {
                tiles.add(new Domino(i, j));
            }
        }
        
        shuffle();
    }

    public void shuffle() {
        Collections.shuffle(tiles);
    }

    public List<Domino> deal(int count) {
        List<Domino> hand = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (!tiles.isEmpty()) {
                hand.add(tiles.remove(0));
            }
        }
        return hand;
    }
    
    public Domino draw() {
        if (tiles.isEmpty()) {
            return null;
        }
        return tiles.remove(0); // Take the top tile off the deck
    }

    public int size() {
        // If you use an ArrayList named 'cards':
        return tiles.size(); 
    
        // OR if you use a standard Array named 'cards':
        // return cards.length;
    }

}

