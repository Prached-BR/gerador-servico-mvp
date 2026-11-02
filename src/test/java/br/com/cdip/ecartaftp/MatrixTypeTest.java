package br.com.cdip.ecartaftp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MatrixTypeTest {

    @Test
    void simplexAceitaAteUmaPaginaComplementar() {
        assertTrue(MatrixType.SIMPLEX.isAllowed(0));
        assertTrue(MatrixType.SIMPLEX.isAllowed(1));
        assertFalse(MatrixType.SIMPLEX.isAllowed(2));
    }

    @Test
    void duplexExigeExatamenteDuasPaginasComplementares() {
        assertFalse(MatrixType.DUPLEX.isAllowed(1));
        assertTrue(MatrixType.DUPLEX.isAllowed(2));
        assertFalse(MatrixType.DUPLEX.isAllowed(3));
    }

    @Test
    void multiplexEInsercaoMantemRotulosERegrasTextuaisEsperadas() {
        assertEquals("Multiplex", MatrixType.MULTIPLEX.toString());
        assertEquals("1 a 7 pagina(s) complementar(es)", MatrixType.MULTIPLEX.getRuleText());
        assertEquals("1 a 198 pagina(s) complementar(es)", MatrixType.INSERCAO.getRuleText());
    }
}
