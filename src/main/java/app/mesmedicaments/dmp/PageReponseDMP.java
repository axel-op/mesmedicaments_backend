package app.mesmedicaments.dmp;

import org.jsoup.nodes.Document;

public class PageReponseDMP {

    protected final Document document;

    protected PageReponseDMP(Document document) {
        this.document = document;
    }

    protected String getSid() {
        return document.getElementsByAttributeValue("name", "sid")
                        .first()
                        .val();
    }

    protected String getTformdata() {
        return document.getElementsByAttributeValue("name", "t:formdata")
                        .first()
                        .val();
    }

}