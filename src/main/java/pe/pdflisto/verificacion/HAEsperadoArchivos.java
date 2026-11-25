package pe.pdflisto.verificacion;

import java.io.File;
import java.util.List;

public sealed interface HAEsperadoArchivos {
    record HAPartes(File haOrigen, String haClave, List<List<Integer>> haPartes) implements HAEsperadoArchivos {
    }

    record HAImagenesAPdf(List<File> haImagenes) implements HAEsperadoArchivos {
    }

    record HATextoAPdf(File haTxt) implements HAEsperadoArchivos {
    }

    record HAWordAPdf(File haDocumento) implements HAEsperadoArchivos {
    }

    record HAPdfAImagenes(File haOrigen, String haClave, int haDpi) implements HAEsperadoArchivos {
    }

    record HAPdfATexto(File haOrigen, String haClave) implements HAEsperadoArchivos {
    }

    record HAExtraerImagenes(File haOrigen, String haClave) implements HAEsperadoArchivos {
    }
}
