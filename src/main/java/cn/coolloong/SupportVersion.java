package cn.coolloong;

public enum SupportVersion {
    MC_OLD(169, 1343),//1.9-1.12
    MC_NEW(2200, 3218);//1.15-1.19.3
    final int start;
    final int end;

    SupportVersion(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public static SupportVersion selectVersion(int dataVersion) {
        for (var ver : SupportVersion.values()) {
            if (dataVersion <= ver.end && dataVersion >= ver.start) {
                return ver;
            }
        }
        return MC_OLD;
    }
}
