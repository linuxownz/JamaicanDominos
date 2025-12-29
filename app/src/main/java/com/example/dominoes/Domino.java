package com.example.dominoes;

public class Domino {
    private int side1;
    private int side2;

    public Domino(int side1, int side2) {
        this.side1 = side1;
        this.side2 = side2;
    }

    public int getLeft() {
        return side1;
    }

    public int getRight() {
        return side2;
    }

    // This physically swaps the numbers so [1|2] becomes [2|1]
    public void flip() {
        int temp = side1;
        side1 = side2;
        side2 = temp;
    }

    public boolean isDouble() {
        return side1 == side2;
    }

    public int getWeight() {
        return side1 + side2;
    }

    @Override
    public String toString() {
        return side1 + "\n---\n" + side2;
    }
}
