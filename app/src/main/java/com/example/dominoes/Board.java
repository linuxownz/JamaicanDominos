package com.example.dominoes;
import java.util.LinkedList;

public class Board {
    private LinkedList<Domino> chain = new LinkedList<>();

    public boolean play(Domino d, boolean toLeft) {
        if (chain.isEmpty()) {
            chain.add(d);
            return true;
        }

        if (toLeft) {
            int boardLeft = chain.getFirst().getLeft();
            if (d.getRight() == boardLeft) {
                chain.addFirst(d);
                return true;
            } else if (d.getLeft() == boardLeft) {
                d.flip(); // Rotate tile to match
                chain.addFirst(d);
                return true;
            }
        } else {
            int boardRight = chain.getLast().getRight();
            if (d.getLeft() == boardRight) {
                chain.addLast(d);
                return true;
            } else if (d.getRight() == boardRight) {
                d.flip(); // Rotate tile to match
                chain.addLast(d);
                return true;
            }
        }
        return false; // Illegal move
    }

    public void showBoard() {
        System.out.println("\nBOARD: " + chain);
        if (!chain.isEmpty()) {
            System.out.println("Ends: [L: " + getLeftEnd() + " | R: " + getRightEnd() + "]");
        }
    }

    public int getLeftEnd() {
        return chain.isEmpty() ? -1 : chain.getFirst().getLeft();
    }

    public int getRightEnd() {
        return chain.isEmpty() ? -1 : chain.getLast().getRight();
    }
}
