package util;

public class NumberParsing {
    public static boolean tryParseToInt(String number) {
        try {
            Integer.parseInt(number);
            return true;
        }  catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean tryParseToDouble(String number) {
        try {
            Double.parseDouble(number);
            return true;
        }  catch (NumberFormatException e) {
            return false;
        }
    }
}
