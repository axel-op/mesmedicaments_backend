package app.mesmedicaments.objets;

import org.json.JSONString;

public abstract
class Pays
implements JSONString
{

    public static Pays fromCode(String code) {
        switch (code) {
            case "france":
                return France.instance;
            case "belgique":
                return Belgique.instance;
        }
        throw new IllegalArgumentException("Pays introuvable pour le code : " + code);
    }

    public final String code;

    private Pays(String code) {
        this.code = code;
    }

    @Override
    public final String toJSONString() {
        return code;
    }

    @Override
    public final String toString() {
        return code;
    }

    @Override
    public final boolean equals(Object other) {
        if (other instanceof Pays) {
            final Pays autre = (Pays) other;
            return autre.code == this.code;
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return code.hashCode();
    }

    public static
    class France
    extends Pays {
        //public static final String code = "france";

        public static final France instance = new France();

        private France() {
            super("france");
        }
    }

    public static
    class Belgique
    extends Pays {
        //public static final String code = "belgique";

        public static final Belgique instance = new Belgique();

        private Belgique() {
            super("belgique");
        }
    }

}