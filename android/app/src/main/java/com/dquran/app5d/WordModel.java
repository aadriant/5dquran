package com.dquran.app5d;

import java.io.Serializable;

public class WordModel implements Serializable {
    private final int position;
    private final String arabic;
    private final String transliteration;
    private final String translation;

    public WordModel(int position, String arabic, String transliteration, String translation) {
        this.position = position;
        this.arabic = arabic;
        this.transliteration = transliteration;
        this.translation = translation;
    }

    public int getPosition() {
        return position;
    }

    public String getArabic() {
        return arabic != null ? arabic : "";
    }

    public String getTransliteration() {
        return transliteration != null ? transliteration : "";
    }

    public String getTranslation() {
        return translation != null ? translation : "";
    }
}
