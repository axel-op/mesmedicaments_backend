package app.mesmedicaments.objets;

import org.json.JSONString;

public enum Langue implements JSONString {

    Francais("francais"), Latin("latin");

    public static Langue getLangue(String code) {
        for (Langue langue : Langue.values()) {
            if (langue.code.equals(code))
                return langue;
        }
        throw new IllegalArgumentException("Le code de langue " + code + " n'existe pas");
    }

    public final String code;

    private Langue(String code) {
        this.code = code;
    }

    @Override
    public String toString() {
        return code;
    }

    @Override
    public String toJSONString() {
        return code;
    }
}