package cn.coolloong;

public enum SupportVersion {
    MC_OLD(169, 1343),//1.9-1.12
    MC_NEW(2200, 3465);//1.15-1.20.1
    final int start;
    final int end;

    SupportVersion(int start, int end) {
        this.start = start;
        this.end = end;
    }

    /**
     * Select version support version.
     *
     * @param dataVersion the data version
     * @return the support version
     */
    public static SupportVersion selectVersion(int dataVersion) throws UnsupportedOperationException {
        for (var ver : SupportVersion.values()) {
            if (dataVersion <= ver.end && dataVersion >= ver.start) {
                return ver;
            }
        }
        throw new UnsupportedOperationException("UnSupport World Version: " + dataVersion);
    }
}
