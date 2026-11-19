// =============================================================================
// Tab2Monitor - Layout (quali blocchi stanno su quale pagina e in quale colonna)
// Versione: v0.13.0 - 2026-11-19 12:00
//   v0.13.0: nuovo. Prima l'organizzazione era fissa nel codice (MonitorView.PAGES / PAGE_NAMES):
//            pagina 1 = {{CPU, Core, Storico}, {RAM, GPU}, {Rete, Dischi, Processi}},
//            pagina 2 = {{Claude}, {Sensori}}. Ora e' un dato salvato nelle preferenze ("layout", JSON)
//            che l'utente cambia dalle impostazioni (pagina di ogni blocco) o con la modalita'
//            "Modifica layout" sul tablet (trascinando i blocchi).
//
// Modello: pagine -> colonne -> id dei blocchi. Gli id sono quelli di MonitorView.PANEL_KEYS, piu' BOXES (11),
// il blocco "Sensori" che raccoglie temperature (5) e ventole (10): 5 e 10 non sono blocchi a se',
// sono solo interruttori di righe dentro BOXES.
// JSON: {"pages":[[[0,1,2],[3,4],[6,7,8]],[[9],[11]]],"names":["Sistema e GPU","Claude e sensori"]}
// (names assente = "Pagina N"). Un layout non valido o incompleto si ripara da solo (blocchi mancanti in
// fondo, doppi e sconosciuti tolti) e senza dati si usa quello predefinito.
// =============================================================================
package it.tab2revamp.monitor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

final class Layout {
    static final int BOXES = 11;
    static final int MAX_PAGES = 5;
    static final int MAX_COLS = 3;           // oltre 3 colonne i blocchi diventano troppo stretti

    /** Nome di ogni blocco, indicizzato per id (5 e 10 non sono blocchi: stringa vuota). */
    static final String[] BLOCK_NAMES = {"CPU", "Core", "Storico 60 s", "RAM", "GPU", "", "Rete", "Dischi",
            "Top processi", "Claude", "", "Sensori"};

    int[][][] pages;                         // pagina -> colonna -> id
    String[] names;                          // null = "Pagina N"

    static boolean isBlock(int id) { return id >= 0 && id <= BOXES && id != 5 && id != 10; }

    static int[] allBlocks() { return new int[]{0, 1, 2, 3, 4, 6, 7, 8, 9, BOXES}; }

    static Layout defaults() {
        Layout l = new Layout();
        l.pages = new int[][][]{{{0, 1, 2}, {3, 4}, {6, 7, 8}}, {{9}, {BOXES}}};
        l.names = new String[]{"Sistema e GPU", "Claude e sensori"};
        return l;
    }

    String pageName(int p) {
        if (names != null && p < names.length && names[p] != null && names[p].length() > 0) return names[p];
        return "Pagina " + (p + 1);
    }

    int pageOf(int id) {
        for (int p = 0; p < pages.length; p++)
            for (int[] col : pages[p]) for (int x : col) if (x == id) return p;
        return -1;
    }

    Layout copy() {
        Layout l = new Layout();
        l.pages = new int[pages.length][][];
        for (int p = 0; p < pages.length; p++) {
            l.pages[p] = new int[pages[p].length][];
            for (int c = 0; c < pages[p].length; c++) l.pages[p][c] = pages[p][c].clone();
        }
        l.names = names == null ? null : names.clone();
        return l;
    }

    // ------------------------------------------------------------ lettura / scrittura
    static Layout parse(String json) {
        if (json == null || json.length() == 0) return defaults();
        try {
            JSONObject o = new JSONObject(json);
            JSONArray pa = o.getJSONArray("pages");
            ArrayList<ArrayList<ArrayList<Integer>>> pg = new ArrayList<ArrayList<ArrayList<Integer>>>();
            boolean[] seen = new boolean[BOXES + 1];
            for (int p = 0; p < pa.length() && pg.size() < MAX_PAGES; p++) {
                JSONArray ca = pa.getJSONArray(p);
                ArrayList<ArrayList<Integer>> cols = new ArrayList<ArrayList<Integer>>();
                for (int c = 0; c < ca.length() && cols.size() < MAX_COLS; c++) {
                    JSONArray ia = ca.getJSONArray(c);
                    ArrayList<Integer> ids = new ArrayList<Integer>();
                    for (int i = 0; i < ia.length(); i++) {
                        int id = ia.getInt(i);
                        if (isBlock(id) && !seen[id]) { seen[id] = true; ids.add(id); }
                    }
                    if (!ids.isEmpty()) cols.add(ids);
                }
                if (!cols.isEmpty()) pg.add(cols);
            }
            if (pg.isEmpty()) return defaults();
            // blocchi mancanti (es. layout salvato da una versione con meno blocchi): in fondo all'ultima pagina
            for (int id : allBlocks()) if (!seen[id]) {
                ArrayList<ArrayList<Integer>> last = pg.get(pg.size() - 1);
                ArrayList<Integer> col = last.get(last.size() - 1);
                col.add(id);
            }
            Layout l = new Layout();
            l.pages = new int[pg.size()][][];
            for (int p = 0; p < pg.size(); p++) {
                l.pages[p] = new int[pg.get(p).size()][];
                for (int c = 0; c < pg.get(p).size(); c++) {
                    ArrayList<Integer> ids = pg.get(p).get(c);
                    l.pages[p][c] = new int[ids.size()];
                    for (int i = 0; i < ids.size(); i++) l.pages[p][c][i] = ids.get(i);
                }
            }
            JSONArray na = o.optJSONArray("names");
            if (na != null) {
                l.names = new String[l.pages.length];
                for (int p = 0; p < l.names.length; p++) l.names[p] = p < na.length() ? na.optString(p, null) : null;
            }
            return l;
        } catch (Exception e) {
            return defaults();
        }
    }

    String toJson() {
        try {
            JSONArray pa = new JSONArray();
            for (int[][] page : pages) {
                JSONArray ca = new JSONArray();
                for (int[] col : page) {
                    JSONArray ia = new JSONArray();
                    for (int id : col) ia.put(id);
                    ca.put(ia);
                }
                pa.put(ca);
            }
            JSONObject o = new JSONObject().put("pages", pa);
            if (names != null) {
                JSONArray na = new JSONArray();
                for (String s : names) na.put(s == null ? "" : s);
                o.put("names", na);
            }
            return o.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------ modifiche
    /** Toglie il blocco da dove sta; le colonne rimaste vuote spariscono (le pagine vuote no: vedi trim()). */
    void remove(int id) {
        for (int p = 0; p < pages.length; p++) {
            ArrayList<int[]> cols = new ArrayList<int[]>();
            for (int[] col : pages[p]) {
                int k = 0;
                for (int x : col) if (x != id) k++;
                if (k == 0) continue;
                int[] n = new int[k];
                int z = 0;
                for (int x : col) if (x != id) n[z++] = x;
                cols.add(n);
            }
            pages[p] = cols.toArray(new int[0][]);
        }
    }

    /** Mette il blocco nella pagina/colonna/posizione indicate; col == numero di colonne = nuova colonna in fondo. */
    void insert(int id, int page, int col, int pos) {
        remove(id);
        if (page >= pages.length) {                       // pagina nuova in fondo
            int[][][] np = new int[page + 1][][];
            System.arraycopy(pages, 0, np, 0, pages.length);
            for (int p = pages.length; p <= page; p++) np[p] = new int[0][];
            pages = np;
            if (names != null) {
                String[] nn = new String[page + 1];
                System.arraycopy(names, 0, nn, 0, Math.min(names.length, nn.length));
                names = nn;
            }
        }
        int[][] cols = pages[page];
        if (col < 0) col = 0;
        if (col >= cols.length) {                         // nuova colonna
            if (cols.length >= MAX_COLS) { col = cols.length - 1; pos = Integer.MAX_VALUE; }
            else {
                int[][] nc = new int[cols.length + 1][];
                System.arraycopy(cols, 0, nc, 0, cols.length);
                nc[cols.length] = new int[]{id};
                pages[page] = nc;
                return;
            }
        }
        int[] old = cols[col];
        if (pos < 0) pos = 0;
        if (pos > old.length) pos = old.length;
        int[] n = new int[old.length + 1];
        System.arraycopy(old, 0, n, 0, pos);
        n[pos] = id;
        System.arraycopy(old, pos, n, pos + 1, old.length - pos);
        cols[col] = n;
    }

    /** Sposta il blocco in una pagina senza sapere dove: pagina vuota o con meno di 3 colonne = nuova colonna,
     *  altrimenti nella colonna con meno blocchi (a parita' l'ultima). */
    void moveToPage(int id, int page) {
        if (pageOf(id) == page) return;
        remove(id);
        if (page >= pages.length) page = pages.length;
        int ncols = page < pages.length ? pages[page].length : 0;
        int weight = 0;
        if (page < pages.length) for (int[] col : pages[page]) weight += colWeight(col);
        weight += id == BOXES ? 2 : 1;
        if (ncols == 0 || (ncols < MAX_COLS && weight <= 4)) { insert(id, page, ncols, 0); return; }
        int best = ncols - 1;
        for (int c = ncols - 1; c >= 0; c--) if (pages[page][c].length < pages[page][best].length) best = c;
        insert(id, page, best, pages[page][best].length);
    }

    /** Larghezza relativa di una colonna: quella che contiene Sensori vale il doppio. */
    static int colWeight(int[] col) {
        for (int id : col) if (id == BOXES) return 2;
        return 1;
    }

    /** Toglie le pagine senza blocchi; con i nomi personalizzati li sposta insieme alle pagine. */
    void trim() {
        ArrayList<int[][]> pg = new ArrayList<int[][]>();
        ArrayList<String> nm = new ArrayList<String>();
        for (int p = 0; p < pages.length; p++) {
            if (pages[p].length == 0) continue;
            pg.add(pages[p]);
            nm.add(names != null && p < names.length ? names[p] : null);
        }
        if (pg.isEmpty()) { Layout d = defaults(); pages = d.pages; names = d.names; return; }
        pages = pg.toArray(new int[0][][]);
        if (names != null) names = nm.toArray(new String[0]);
    }
}
