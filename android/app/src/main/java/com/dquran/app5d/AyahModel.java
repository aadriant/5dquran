package com.dquran.app5d;

import java.io.Serializable;

public class AyahModel implements Serializable {
    private int numberInSurah;
    private int number;
    private String arabic;
    private String indo;

    public AyahModel(int numberInSurah, int number, String arabic, String indo) {
        this.numberInSurah = numberInSurah;
        this.number = number;
        this.arabic = arabic;
        this.indo = indo;
    }

    public int getNumberInSurah() { return numberInSurah; }
    public int getNumber() { return number; }
    public String getArabic() { return arabic; }
    public String getIndo() { return indo; }
}
