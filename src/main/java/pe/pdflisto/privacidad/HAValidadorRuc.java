package pe.pdflisto.privacidad;

public final class HAValidadorRuc {
    private static final int[] HA_PESOS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private HAValidadorRuc() {
    }

    public static boolean haValido(String haRuc) {
        if (haRuc == null || !haRuc.matches("\\d{11}")) {
            return false;
        }
        String haPrefijo = haRuc.substring(0, 2);
        if (!(haPrefijo.equals("10") || haPrefijo.equals("15") || haPrefijo.equals("16")
                || haPrefijo.equals("17") || haPrefijo.equals("20"))) {
            return false;
        }
        int haSuma = 0;
        for (int haI = 0; haI < 10; haI++) {
            haSuma += (haRuc.charAt(haI) - '0') * HA_PESOS[haI];
        }
        int haDigito = 11 - (haSuma % 11);
        if (haDigito == 10) {
            haDigito = 0;
        } else if (haDigito == 11) {
            haDigito = 1;
        }
        return haDigito == haRuc.charAt(10) - '0';
    }
}
