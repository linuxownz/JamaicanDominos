package com.example.dominoes;
import java.util.ArrayList;
import java.util.List;
//import java.util.Scanner;

public class Player {
    public String name;
    public List<Domino> hand;

    public Player(String name) {
        this.name = name;
        this.hand = new ArrayList<>();
    }

    // Displays the hand so the user can pick a number
    public void showHand() {
        System.out.println(name + "'s Hand:");
        for (int i = 0; i < hand.size(); i++) {
            System.out.print(i + ":" + hand.get(i) + " ");
        }
        System.out.println("\n");
    }

    // Checks if this player has the 6-6 to start the game
    public int getStartingDouble() {
        for (int i = 0; i < hand.size(); i++) {
            if (hand.get(i).getLeft() == 6 && hand.get(i).getRight() == 6) return i;
        }
        return -1;
    }

    public Domino findMove(int leftEnd, int rightEnd) {
        // If board is empty, any tile works (though usually 6-6 starts)
        if (leftEnd == -1) return hand.get(0); 

        for (Domino d : hand) {
            if (d.getLeft() == leftEnd || d.getRight() == leftEnd ||
                d.getLeft() == rightEnd || d.getRight() == rightEnd) {
                return d;
            }
        }
        return null; // No matching tiles
    }

    public List<Integer> getValidMoves(int leftEnd, int rightEnd) {
        List<Integer> validIndices = new ArrayList<>();
    
        // If board is empty, every tile is a valid starting move
        if (leftEnd == -1) {
            for (int i = 0; i < hand.size(); i++) validIndices.add(i);
            return validIndices;
        }

        for (int i = 0; i < hand.size(); i++) {
            Domino d = hand.get(i);
            if (d.getLeft() == leftEnd || d.getRight() == leftEnd ||
                d.getLeft() == rightEnd || d.getRight() == rightEnd) {
                validIndices.add(i);
            }
        }
        return validIndices;
    }
}
