package ui;

import controller.CommandController;

import java.util.Scanner;

public class App {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        String input;
        String resultado;
        System.out.println("Mini interprete de comandos de Java");

        for (;;) {
            System.out.print("pspsh>");
            input = sc.nextLine().trim();

            resultado = CommandController.handle(input);
            System.out.println(resultado);
        }
    }
}