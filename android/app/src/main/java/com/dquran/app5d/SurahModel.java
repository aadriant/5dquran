package com.dquran.app5d;

import java.io.Serializable;

public class SurahModel implements Serializable {
    private int number;
    private String name;
    private String englishName;
    private String indonesianName;
    private int numberOfAyahs;
    private String revelationType;

    public SurahModel(int number, String name, String englishName, String indonesianName, int numberOfAyahs, String revelationType) {
        this.number = number;
        this.name = name;
        this.englishName = englishName;
        this.indonesianName = indonesianName;
        this.numberOfAyahs = numberOfAyahs;
        this.revelationType = revelationType;
    }

    public int getNumber() { return number; }
    public String getName() { return name; }
    public String getEnglishName() { return englishName; }
    public String getIndonesianName() { return indonesianName != null ? indonesianName : englishName; }
    public int getNumberOfAyahs() { return numberOfAyahs; }
    public String getRevelationType() { return revelationType; }
}
