package cn.coolloong.utils;

import cn.coolloong.PNXWorldConverter;
import cn.coolloong.proxy.ProxyChunk;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockstate.BlockState;
import cn.nukkit.level.terra.handles.JeBlockState;
import cn.nukkit.level.terra.handles.PNXWorldHandle;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.utils.Config;
import com.google.gson.Gson;
import org.jglrxavpok.hephaistos.mca.ChunkColumn;
import org.jglrxavpok.hephaistos.nbt.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

public class DataConvert {
    private static final Map<String, Map<String, Object>> BLOCKS_MAP = new HashMap<>();
    private static final Map<String, Integer> BIOMES_MAP = new HashMap<>();
    private static final Map<String, Map<String, Object>> ITEM_MAP = new HashMap<>();
    private static final Map<String, Short> ENCHANTMENT_MAP = new HashMap<>();
    private static final Gson GSON = new Gson();

    static {
        final var config = new Config(Config.JSON);

        try {
            config.load(PNXWorldHandle.class.getModule().getResourceAsStream("jeMappings/jeBlocksMapping.json"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //noinspection unchecked
        config.getAll().forEach((k, v) -> BLOCKS_MAP.put(k, (Map<String, Object>) v));

        try {
            config.load(PNXWorldConverter.class.getModule().getResourceAsStream("jeBiomesMapping.json"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        config.getAll().forEach((k, v) -> BIOMES_MAP.put(k, ((Number) v).intValue()));

        try {
            config.load(PNXWorldConverter.class.getModule().getResourceAsStream("jeItemsMapping.json"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //noinspection unchecked
        config.getAll().forEach((k, v) -> ITEM_MAP.put(k, (Map<String, Object>) v));

        try {
            config.load(PNXWorldConverter.class.getModule().getResourceAsStream("jeEnchantmentsMapping.json"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        config.getAll().forEach((k, v) -> ENCHANTMENT_MAP.put(k, ((Number) v).shortValue()));
    }

    public static BlockState convertBlockState(org.jglrxavpok.hephaistos.mca.BlockState sourceType) {
        JeBlockState jeState;
        if (!sourceType.getProperties().isEmpty()) {
            var state = new StringBuilder();
            sourceType.getProperties().forEach((k, v) -> state.append(k).append("=").append(v).append(","));
            jeState = new JeBlockState(sourceType.getName() + "[" + state + "]");
        } else jeState = new JeBlockState(sourceType.getName());

        var beState = PNXWorldHandle.jeBlocksMapping.get(jeState);
        String beBlockName = null;
        Map<String, Object> beBlockState = null;
        if (beState != null) {
            beBlockName = beState.get("bedrock_identifier").toString();
            //noinspection unchecked
            beBlockState = (Map<String, Object>) beState.get("bedrock_states");
        } else {//由于不同版本数据值可能存在一定区别，当前映射文件是最新的，所以找不到就模糊匹配
            for (var key : BLOCKS_MAP.keySet()) {
                if (key.contains(sourceType.getName())) {
                    if (jeState.getAttributes().keySet().stream().anyMatch(key::contains)) {
                        beBlockName = BLOCKS_MAP.get(key).get("bedrock_identifier").toString();
                        //noinspection unchecked
                        beBlockState = (Map<String, Object>) BLOCKS_MAP.get(key).get("bedrock_states");
                    }
                }
            }
        }
        if (beBlockState != null) {
            var nkState = new StringBuilder();
            beBlockState.forEach((k, v) -> {
                if (v.toString().equals("true") || v.toString().equals("false")) {
                    nkState.append(";").append(k).append("=").append(v.equals("true") ? 1 : 0);
                } else if (v instanceof Number number) {
                    nkState.append(";").append(k).append("=").append(number.intValue());
                } else nkState.append(";").append(k).append("=").append(v);
            });
            return BlockState.of(beBlockName + nkState);
        } else {
            try {
                return BlockState.of(beBlockName + "");
            } catch (NoSuchElementException e) {
                return BlockState.AIR;
            }
        }
    }

    public static int convertBiomes(String sourceType) {
        try {
            return BIOMES_MAP.get(sourceType);
        } catch (Exception e) {
            System.out.println(sourceType);
            throw e;
        }
    }

    @SuppressWarnings("RedundantLabeledSwitchRuleCodeBlock")
    public static cn.nukkit.nbt.tag.Tag convertNBT(org.jglrxavpok.hephaistos.nbt.NBT sourceType) {
        switch (sourceType.getID().getOrdinal()) {
            case 0 -> {
                return new EndTag();
            }
            case 1 -> {
                return new ByteTag("", ((NBTByte) sourceType).getValue());
            }
            case 2 -> {
                return new ShortTag("", ((NBTShort) sourceType).getValue());
            }
            case 3 -> {
                return new IntTag("", ((NBTInt) sourceType).getValue());
            }
            case 4 -> {
                return new LongTag("", ((NBTLong) sourceType).getValue());
            }
            case 5 -> {
                return new FloatTag("", ((NBTFloat) sourceType).getValue());
            }
            case 6 -> {
                return new DoubleTag("", ((NBTDouble) sourceType).getValue());
            }
            case 7 -> {
                return new ByteArrayTag("", ((NBTByteArray) sourceType).getValue().copyArray());
            }
            case 8 -> {
                return new StringTag("", ((NBTString) sourceType).getValue());
            }
            case 9 -> {
                var list = ((NBTList<?>) sourceType);
                var tags = new ArrayList<Tag>();
                switch (list.getSubtagType().getOrdinal()) {
                    case 0 -> {
                        list.forEach((Consumer<NBT>) k -> tags.add(new EndTag()));
                    }
                    case 1 -> {
                        list.forEach((Consumer<NBT>) k -> tags.add(new ByteTag("", ((NBTByte) k).getValue())));
                    }
                    case 2 -> {
                        list.forEach((Consumer<NBT>) k -> tags.add(new ShortTag("", ((NBTShort) k).getValue())));
                    }
                    case 3 -> {
                        list.forEach((Consumer<NBT>) k -> tags.add(new IntTag("", ((NBTInt) k).getValue())));
                    }
                    case 4 -> {
                        list.forEach((Consumer<NBT>) k -> tags.add(new LongTag("", ((NBTLong) k).getValue())));
                    }
                    case 5 -> {
                        list.forEach((Consumer<NBT>) k -> tags.add(new FloatTag("", ((NBTFloat) k).getValue())));
                    }
                    case 6 -> {
                        list.forEach((Consumer<NBT>) k -> tags.add(new DoubleTag("", ((NBTDouble) k).getValue())));
                    }
                    case 7 -> {
                        list.forEach((Consumer<NBT>) k -> tags.add(new ByteArrayTag("", ((NBTByteArray) k).getValue().copyArray())));
                    }
                    case 8 -> {
                        list.forEach((Consumer<NBT>) k -> tags.add(new StringTag("", ((NBTString) k).getValue())));
                    }
                    case 9, 10 -> {
                        list.forEach((Consumer<NBT>) k -> tags.add(convertNBT(k)));
                    }
                    case 11 -> {
                        list.forEach((Consumer<NBT>) k -> tags.add(new IntArrayTag("", ((NBTIntArray) k).getValue().copyArray())));
                    }
                    case 12 -> {
                        list.forEach((Consumer<NBT>) k -> {
                            var result = new ListTag<LongTag>("");
                            for (long i : ((NBTLongArray) k).getValue().copyArray()) {
                                result.add(new LongTag("", i));
                            }
                            tags.add(result);
                        });
                    }
                }
                var result = new ListTag<>("");
                for (var value : tags) {
                    result.add(value);
                }
                return result;
            }
            case 10 -> {
                var list = ((NBTCompound) sourceType);
                var cmp = new CompoundTag("");
                for (var entry : list.getEntries()) {
                    cmp.put(entry.getKey(), convertNBT(entry.getValue()));
                }
                return cmp;
            }
            case 11 -> {
                return new IntArrayTag("", ((NBTIntArray) sourceType).getValue().copyArray());
            }
            case 12 -> {
                var result = new ListTag<LongTag>("");
                for (long i : ((NBTLongArray) sourceType).getValue().copyArray()) {
                    result.add(new LongTag("", i));
                }
                return result;
            }
        }
        return new EndTag();
    }

    public static void convertItem(String sourceType, CompoundTag compoundTag) {
        String bedrock_identifier = (String) ITEM_MAP.get(sourceType).get("bedrock_identifier");
        short bedrock_data = ((Number) ITEM_MAP.get(sourceType).get("bedrock_data")).shortValue();
        compoundTag.putString("Name", bedrock_identifier);
        compoundTag.putShort("Damage", bedrock_data);
        compoundTag.remove("id");
    }

    public static short convertEnchantment(String sourceType) {
        return ENCHANTMENT_MAP.get(sourceType);
    }

    //投掷器 发射器无法打开 末影箱物品 漏斗物品
    public static void convertTileEntities(ProxyChunk chunk, ChunkColumn chunkColumn, org.jglrxavpok.hephaistos.nbt.NBTList<NBTCompound> tileEntities) {
        var chestList = new ConcurrentLinkedDeque<CompoundTag>();
        tileEntities.asListView().forEach(nbt -> {
            var cmp = (CompoundTag) convertNBT(nbt);
            cmp.remove("keepPacked");
            if (cmp.contains("Items")) {
                for (var i : cmp.getList("Items", CompoundTag.class).getAll()) {
                    if (i.containsCompound("tag")) {
                        var tag = i.getCompound("tag");
                        if (tag.containsList("Enchantments")) {
                            var list = tag.getList("Enchantments", CompoundTag.class);
                            list.getAll().forEach(ench -> ench.putShort("id", convertEnchantment(ench.getString("id"))));
                            tag.put("ench", list);
                            tag.remove("Enchantments");
                        }
                        if (tag.contains("Damage")) tag.remove("Damage");//不知道干啥的nbt,pnx没有所以移除
                        if (tag.contains("display")) {
                            var jsonName = GSON.fromJson(tag.getCompound("display").getString("Name"), Map.class);
                            tag.getCompound("display").putString("Name", jsonName.get("text").toString());
                        }
                    }
                    convertItem(i.getString("id"), i);
                }
            }
            //noinspection ConstantConditions
            switch (nbt.get("id").getValue().toString()) {
                case "minecraft:chest", "minecraft:trapped_chest" -> {
                    //noinspection ConstantConditions
                    var block = chunkColumn.getBlockState(((int) nbt.get("x").getValue()) & 15, (int) nbt.get("y").getValue(), ((int) nbt.get("z").getValue()) & 15);
                    if (block.getProperties().get("type").equals("single")) {
                        cmp.putString("id", BlockEntity.CHEST);
                        chunk.tiles.add(cmp);
                    } else {
                        cmp.putString("id", BlockEntity.CHEST);
                        chestList.add(cmp);
                    }
                }
                case "minecraft:furnace" -> {
                    cmp.putString("id", BlockEntity.FURNACE);
                    chunk.tiles.add(cmp);
                }
                case "minecraft:barrel" -> {
                    cmp.putString("id", BlockEntity.BARREL);
                    chunk.tiles.add(cmp);
                }
                case "minecraft:shulker_box" -> {
                    //noinspection ConstantConditions
                    var block = chunkColumn.getBlockState(((int) nbt.get("x").getValue()) & 15, (int) nbt.get("y").getValue(), ((int) nbt.get("z").getValue()) & 15);
                    byte facing = switch (block.getProperties().get("facing")) {
                        case "up" -> 1;
                        case "north" -> 2;
                        case "south" -> 3;
                        case "west" -> 4;
                        case "east" -> 5;
                        default -> 0;
                    };
                    cmp.putString("id", BlockEntity.SHULKER_BOX);
                    cmp.putByte("facing", facing);
                    chunk.tiles.add(cmp);
                }
                case "minecraft:beacon" -> {
                    cmp.putString("id", BlockEntity.BEACON);
                    chunk.tiles.add(cmp);
                }
                case "minecraft:beehive" -> {
                    cmp.putString("id", BlockEntity.BEEHIVE);
                    chunk.tiles.add(cmp);
                }
            }
        });
        for (var pair1 : chestList) {
            for (var pair2 : chestList) {
                int pair1y = pair1.getInt("y");
                int pair2y = pair2.getInt("y");
                if (pair1y == pair2y) {
                    int pair1x = pair1.getInt("x");
                    int pair2x = pair2.getInt("x");
                    int pair1z = pair1.getInt("z");
                    int pair2z = pair2.getInt("z");
                    if ((pair1z == pair2z && Math.abs(pair1x - pair2x) <= 1) || (pair1x == pair2x && Math.abs(pair1z - pair2z) <= 1)) {
                        pair1.putInt("pairx", pair2x);
                        pair1.putInt("pairz", pair2z);
                        pair2.putInt("pairx", pair1x);
                        pair2.putInt("pairz", pair1z);
                        chunk.tiles.add(pair1);
                        chunk.tiles.add(pair2);
                        chestList.remove(pair1);
                        chestList.remove(pair2);
                    }
                }
            }
        }
    }
}
