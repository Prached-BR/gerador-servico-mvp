package br.com.cdip.ecartaftp;

enum MatrixType {
    SIMPLEX("Simplex", 0, 1),
    DUPLEX("Duplex", 2, 2),
    MULTIPLEX("Multiplex", 1, 7),
    INSERCAO("Insercao", 1, 198);

    private final String label;
    private final int minPages;
    private final int maxPages;

    MatrixType(String label, int minPages, int maxPages) {
        this.label = label;
        this.minPages = minPages;
        this.maxPages = maxPages;
    }

    public String getLabel() {
        return label;
    }

    public boolean isAllowed(int pages) {
        return pages >= minPages && pages <= maxPages;
    }

    public String getRuleText() {
        if (minPages == maxPages) {
            return minPages + " pagina(s) complementar(es)";
        }
        return minPages + " a " + maxPages + " pagina(s) complementar(es)";
    }

    @Override
    public String toString() {
        return label;
    }
}
