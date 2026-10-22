package com.dquran.app5d;

import java.io.Serializable;
import java.util.List;

public class MushafPageModel implements Serializable {
    private final int pageNumber;
    private final int rukuNumber;
    private final int juzNumber;
    private final List<AyahModel> ayahs;
    private final boolean isStartOfSurah;

    public MushafPageModel(int pageNumber, int rukuNumber, int juzNumber, List<AyahModel> ayahs, boolean isStartOfSurah) {
        this.pageNumber = pageNumber;
        this.rukuNumber = rukuNumber;
        this.juzNumber = juzNumber;
        this.ayahs = ayahs;
        this.isStartOfSurah = isStartOfSurah;
    }

    public int getPageNumber() { return pageNumber; }
    public int getRukuNumber() { return rukuNumber; }
    public int getJuzNumber() { return juzNumber; }
    public List<AyahModel> getAyahs() { return ayahs; }
    public boolean isStartOfSurah() { return isStartOfSurah; }
}
