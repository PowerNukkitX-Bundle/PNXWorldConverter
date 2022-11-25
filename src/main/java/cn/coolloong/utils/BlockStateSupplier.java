package cn.coolloong.utils;

@FunctionalInterface
public interface BlockStateSupplier {
    org.jglrxavpok.hephaistos.mca.BlockState get(int rx, int y, int rz);
}
