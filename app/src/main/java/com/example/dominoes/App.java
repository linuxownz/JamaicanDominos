package com.example.dominoes;
import java.util.*;

public class App {
    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        Deck deck = new Deck(); 
        Board board = new Board();
        
        // --- NEW: Move History Log ---
        LinkedList<String> moveLog = new LinkedList<>();
        
        List<Player> players = new ArrayList<>();
        players.add(new Player("You (P1)"));
        players.add(new Player("CPU 2"));
        players.add(new Player("CPU 3"));
        players.add(new Player("CPU 4"));

        for (Player p : players) {
            for (int i = 0; i < 7; i++) {
                Domino d = deck.draw();
                if (d != null) p.hand.add(d);
            }
        }

        int turn = 0;
        for (int i = 0; i < players.size(); i++) {
            for (Domino d : players.get(i).hand) {
                if (d.getLeft() == 6 && d.getRight() == 6) {
                    turn = i;
                    moveLog.add(players.get(i).name + " posed the [6|6]");
                }
            }
        }

        int passCount = 0; 
        boolean gameOver = false;

        while (!gameOver) {
            clearScreen();
            System.out.println("=== JAMAICAN DOMINOES ===");
            
            // --- NEW: Display the Log ---
            System.out.println("Recent Moves:");
            if (moveLog.isEmpty()) System.out.println("  (Game Start)");
            for (String log : moveLog) {
                System.out.println("  > " + log);
            }
            System.out.println("-------------------------");

            board.showBoard();
            
            Player p = players.get(turn);
            boolean moveMade = false;
            String actionSummary = "";

            if (turn == 0) { // HUMAN TURN
                p.showHand();
                List<Integer> hints = p.getValidMoves(board.getLeftEnd(), board.getRightEnd());
                
                if (hints.isEmpty()) {
                    System.out.println("--> No moves! Type -1 to pass.");
                } else {
                    System.out.println("--> Playable indices: " + hints);
                }

                try {
                    System.out.print("Pick index (or -1 to pass): ");
                    int choice = input.nextInt();

                    if (choice == -1) {
                        if (!hints.isEmpty()) {
                            System.out.println("!!! You must play if you can! !!!");
                            pause(input);
                            continue;
                        }
                        actionSummary = p.name + " knocked";
                    } else {
                        Domino d = p.hand.get(choice);
                        int bL = board.getLeftEnd();
                        int bR = board.getRightEnd();

                        boolean fitsLeft = (bL == -1) || (d.getLeft() == bL || d.getRight() == bL);
                        boolean fitsRight = (bL == -1) || (d.getLeft() == bR || d.getRight() == bR);

                        if (bL == -1) { 
                            board.play(d, true);
                            actionSummary = p.name + " played " + d;
                            p.hand.remove(choice);
                            moveMade = true;
                        } else if (fitsLeft && fitsRight && bL != bR) {
                            System.out.print("Fits both! Left (L) or Right (R)? ");
                            String side = input.next().toUpperCase();
                            boolean playToLeft = side.startsWith("L");
                            board.play(d, playToLeft);
                            actionSummary = p.name + " played " + d + (playToLeft ? " Left" : " Right");
                            p.hand.remove(choice);
                            moveMade = true;
                        } else if (fitsLeft) {
                            board.play(d, true);
                            actionSummary = p.name + " played " + d + " Left";
                            p.hand.remove(choice);
                            moveMade = true;
                        } else if (fitsRight) {
                            board.play(d, false);
                            actionSummary = p.name + " played " + d + " Right";
                            p.hand.remove(choice);
                            moveMade = true;
                        } else {
                            System.out.println("!!! INVALID MOVE !!!");
                            pause(input);
                            continue;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("!!! INPUT ERROR !!!");
                    input.nextLine();
                    pause(input);
                    continue;
                }
            } else { // CPU TURN
                System.out.println("\n" + p.name + " is thinking...");
                try { Thread.sleep(1000); } catch (Exception e) {}

                Domino cpuChoice = p.findMove(board.getLeftEnd(), board.getRightEnd());
                if (cpuChoice != null) {
                    boolean playedLeft = false;
                    if (board.getLeftEnd() == -1 || cpuChoice.getLeft() == board.getLeftEnd() || cpuChoice.getRight() == board.getLeftEnd()) {
                        board.play(cpuChoice, true);
                        playedLeft = true;
                    } else {
                        board.play(cpuChoice, false);
                    }
                    actionSummary = p.name + " played " + cpuChoice + (playedLeft ? " Left" : " Right");
                    p.hand.remove(cpuChoice);
                    moveMade = true;
                } else {
                    actionSummary = p.name + " knocked";
                }
            }

            // --- Update History ---
            if (!actionSummary.isEmpty()) {
                moveLog.add(actionSummary);
                if (moveLog.size() > 3) moveLog.removeFirst(); // Keep only last 3
            }

            if (p.hand.isEmpty()) {
                clearScreen();
                board.showBoard();
                System.out.println("\nDOMINO! " + p.name + " wins!");
                gameOver = true;
            } else {
                if (moveMade) passCount = 0; else passCount++;
                if (passCount >= 4) {
                    System.out.println("\nGAME BLOCKED!");
                    determineWinner(players);
                    gameOver = true;
                } else {
                    turn = (turn + 1) % 4;
                }
            }
        }
        input.close();
    }

    public static void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    public static void pause(Scanner input) {
        System.out.print("Press Enter to continue...");
        new Scanner(System.in).nextLine();
    }

    public static void determineWinner(List<Player> players) {
        Player winner = null;
        int lowestScore = Integer.MAX_VALUE;
        for (Player p : players) {
            int score = 0;
            for (Domino d : p.hand) score += d.getWeight();
            System.out.println(p.name + " pips: " + score);
            if (score < lowestScore) {
                lowestScore = score;
                winner = p;
            }
        }
        System.out.println("\n*** Winner: " + (winner != null ? winner.name : "Draw") + " ***");
    }
}
