package app.mesmedicaments.dmp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

import org.json.JSONObject;

import app.mesmedicaments.IJSONSerializable;
import app.mesmedicaments.utils.Utils;

public class DonneesConnexion implements IJSONSerializable {
    protected final Map<String, String> cookies;
    protected final String sid;
    protected final String tformdata;
    public final LocalDateTime date;

    private DonneesConnexion(Map<String, String> cookies, String sid, String tformdata, LocalDateTime date) {
        this.cookies = cookies;
        this.sid = sid;
        this.tformdata = tformdata;
        this.date = date;
    }

    protected DonneesConnexion(Map<String, String> cookies, String sid, String tformdata) {
        this(cookies, sid, tformdata, LocalDateTime.now(Utils.TIMEZONE));
    }

    protected DonneesConnexion(Map<String, String> cookies, PageReponseDMP page) {
        this(cookies, page.getSid(), page.getTformdata());
    }

    public DonneesConnexion(JSONObject donneesConnexion) {
        this(donneesConnexion.getJSONObject("cookies")
                            .keySet()
                            .stream()
                            .collect(Collectors.toMap(
                                        k -> k, 
                                        k -> donneesConnexion.getJSONObject("cookies")
                                                            .getString(k))), 
            donneesConnexion.optString("sid"),
            donneesConnexion.optString("tformdata"),
            LocalDateTime.parse(donneesConnexion.getString("date"),
                                DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    /**
     * Renvoie un JSON contenant les données nécessaires au maintien de la connexion
     * entre les deux étapes. Cet objet doit être restitué tel quel pour la deuxième
     * étape.
     */
    public JSONObject toJSON() {
        return new JSONObject()
            .put("sid", sid)
            .put("tformdata", tformdata)
            .put("cookies", new JSONObject(cookies))
            .put("date", date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
}