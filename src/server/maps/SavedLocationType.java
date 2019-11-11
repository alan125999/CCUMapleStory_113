package server.maps;

public enum SavedLocationType {

    FREE_MARKET(0),
    MULUNG_TC(1),
    WORLDTOUR(2),
    FLORINA(3),
    FISHING(4),
    RICHIE(5),
    DONGDONGCHIANG(6),
    EVENT(7),
    AMORIA(8),
    CHRISTMAS(9),
    MONSTER_CARNIVAL(10),
    PVP(11),
    HOTEL(12),
    PACH(13),
    ENGLISH(14),
    SLEEP(15),
    BIRTHDAY(16),
    PACHINKO(17);
    private final int index;

    private SavedLocationType(int index) {
        this.index = index;
    }

    public int getValue() {
        return index;
    }

    public static SavedLocationType fromString(String Str) {
        return valueOf(Str);
    }
}
